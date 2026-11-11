package dev.pako.bfi.effects

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.ZipFile

/** Imports only a user-selected local package; paths never escape its private directory. */
object ShaderImports {
    private fun root(context:Context)=File(context.filesDir,"shader-imports")
    fun choices(context:Context):List<ShaderChoice> = root(context).listFiles().orEmpty().filter{it.name.matches(Regex("[0-9a-f]{24}"))}.mapNotNull { dir ->
        runCatching {
            val meta=Properties().apply{File(dir,"meta.properties").inputStream().use{load(it)}}
            val preset=meta.getProperty("preset")?:error("Missing preset")
            ShaderPackage.entryPath(preset)
            ShaderChoice("import_${dir.name}",meta.getProperty("label")?:preset,"imports/${dir.name}/$preset")
        }.getOrNull()
    }.sortedBy{it.label}
    fun open(context:Context,path:String):InputStream {
        val parts=path.split('/',limit=3)
        require(parts.size==3 && parts[0]=="imports" && parts[1].matches(Regex("[0-9a-f]{24}"))){"Invalid imported shader path"}
        ShaderPackage.entryPath(parts[2])
        return File(root(context),"${parts[1]}/data/${parts[2]}").inputStream()
    }
    fun prepare(context:Context,uri:Uri):ShaderPackage {
        val file=File.createTempFile("shader-import-",".bin",context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input){"Cannot read the selected file"}
                file.outputStream().use{output->
                    val buffer=ByteArray(65536);var total=0L
                    while(true){val n=input.read(buffer);if(n<0)break;total+=n;require(total<=512L*1024*1024){"Shader archive exceeds 512 MiB"};output.write(buffer,0,n)}
                }
            }
            val name=runCatching {
                context.contentResolver.query(uri,arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),null,null,null)?.use { cursor ->
                    if(cursor.moveToFirst())cursor.getString(0)else null
                }
            }.getOrNull()
            return ShaderPackage(file,name)
        }catch(error:Exception){file.delete();throw error}
    }
    fun install(context:Context,archive:ShaderPackage,preset:String):ShaderChoice {
        val digest=MessageDigest.getInstance("SHA-256")
        archive.file.inputStream().use { input->val bytes=ByteArray(65536);while(true){val n=input.read(bytes);if(n<0)break;digest.update(bytes,0,n)} }
        digest.update(preset.toByteArray())
        val id=digest.digest().joinToString(""){"%02x".format(it)}.take(24)
        val root=root(context).apply{mkdirs()};val destination=File(root,id)
        if(destination.isDirectory)return choices(context).first{it.id=="import_$id"}
        require(root.listFiles().orEmpty().count{it.isDirectory}<32){"Import library is full (32 packages)"}
        val staging=File(root,"pending-$id").apply{deleteRecursively();mkdirs()}
        try {
            val choice=archive.extract(id,preset,File(staging,"data"))
            Properties().apply{setProperty("preset",preset);setProperty("label",choice.label)}.also{properties->File(staging,"meta.properties").outputStream().use{properties.store(it,"Local shader import")}}
            check(staging.renameTo(destination)){"Could not save shader import"}
            return choice
        }finally{staging.deleteRecursively()}
    }
}

/** A ZIP containing .slangp plus dependencies, or a self-contained .slang file. */
class ShaderPackage(val file:File,private val displayName:String?=null):AutoCloseable {
    private val zip:ZipFile?
    private val entries:Map<String,java.util.zip.ZipEntry>
    val presets:List<String>
    init {
        val signature=file.inputStream().use{it.readUpTo(2)}
        zip=if(signature.contentEquals(byteArrayOf(80,75)))ZipFile(file)else null
        try {
            val all=mutableListOf<java.util.zip.ZipEntry>()
            val iterator=zip?.entries();var count=0
            while(iterator?.hasMoreElements()==true){
                require(++count<=50_000){"Archive has too many files"}
                val entry=iterator.nextElement();if(!entry.isDirectory)all+=entry
            }
            all.forEach{entryPath(it.name)}
            entries=all.associateBy{it.name}
            require(entries.size==all.size){"Archive has duplicate paths"}
            presets=if(zip==null)listOf("source.slangp")else entries.keys.filter{it.endsWith(".slangp",true)}.sorted()
            require(presets.isNotEmpty()){"ZIP must contain a .slangp preset and its dependencies"}
            if(zip==null){require(file.length()<=2*1024*1024){"Single shader exceeds 2 MiB"};require(file.readText().contains("#pragma stage")){"Choose a .slang file or a ZIP containing .slangp and dependencies"}}
        }catch(error:Exception){zip?.close();throw error}
    }
    fun extract(id:String,preset:String,destination:File):ShaderChoice {
        require(preset in presets){"Unknown shader preset"}
        val prefix="imports/$id/"
        val label=if(zip==null && !displayName.isNullOrBlank())displayName.substringAfterLast('/').substringBeforeLast('.').take(80) else preset.substringAfterLast('/').substringBeforeLast('.')
        val choice=ShaderChoice("import_$id",label+" (imported)",prefix+preset)
        var total=0L
        val copied=mutableSetOf<String>()
        fun copy(path:String,limit:Int):ByteArray {
            require(path.startsWith(prefix)){"Shader dependency escapes imported package"}
            val entry=path.removePrefix(prefix);entryPath(entry)
            val bytes=if(zip==null) {
                when(entry){"source.slangp"->"shaders=1\nshader0=source.slang\nscale_type0=viewport\n".toByteArray();"source.slang"->file.readBytes();else->error("Missing $entry. For shaders with includes, import a ZIP with all dependencies.")}
            }else {
                val item=entries[entry]?:error("Missing shader dependency: $entry")
                require(item.size<=limit){"Shader dependency is too large: $entry"}
                zip.getInputStream(item).use{input->input.readUpTo(limit+1)}.also{require(it.size<=limit){"Shader dependency is too large: $entry"}}
            }
            if(copied.add(entry)) {
                total+=bytes.size;require(total<=64L*1024*1024){"Selected preset dependencies exceed 64 MiB"}
                File(destination,entry).apply{parentFile?.mkdirs();writeBytes(bytes)}
            }
            return bytes
        }
        var textBudget=8*1024*1024
        val definition=ShaderCatalog.loadChoice(choice){path->
            val bytes=copy(path,2*1024*1024);textBudget-=bytes.size
            require(textBudget>=0){"Shader sources expand beyond 8 MiB"};bytes.toString(Charsets.UTF_8)
        }
        definition.passes.forEachIndexed { index,pass ->
            require(pass.path.endsWith(".slang",true)){"Only Slang shader stages can be imported"}
            shaderTargetFormat(definition.settings,index,pass.source)
            ShaderSource.compile(pass.source,true,true);ShaderSource.compile(pass.source,false,true)
        }
        definition.lookups.forEach{copy(it.path,32*1024*1024)}
        return choice
    }
    override fun close(){zip?.close();file.delete()}
    companion object {
        internal fun entryPath(path:String) {
            require(path.isNotBlank() && path.length<=512 && !path.startsWith('/') && !path.contains('\\') && !path.contains(':') && path.split('/').none{it==".." || it=="." || it.isEmpty()}){"Unsafe shader archive path: $path"}
        }
    }
}

private fun InputStream.readUpTo(limit:Int):ByteArray {
    val output=java.io.ByteArrayOutputStream(minOf(limit,65536));val buffer=ByteArray(minOf(limit,65536))
    while(output.size()<limit){val n=read(buffer,0,minOf(buffer.size,limit-output.size()));if(n<0)break;if(n>0)output.write(buffer,0,n)}
    return output.toByteArray()
}
