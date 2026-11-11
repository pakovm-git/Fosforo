package dev.pako.bfi.effects

import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.ZipFile

/** Immutable source snapshots preserve relative dependencies and original notices. */
internal object ShaderLibraryArchive {
    const val MAX_ARCHIVE=128L*1024*1024
    private const val MAX_EXPANDED=256L*1024*1024
    private const val MAX_FILE=32L*1024*1024
    fun revision(value:String):String {
        require(value.matches(Regex("[0-9a-f]{40}"))){"Invalid source revision"}
        return value
    }
    fun choices(folder:File,revision:String):List<ShaderChoice> {
        revision(revision)
        val index=File(folder,".bfi-index")
        require(index.length()<=4*1024*1024){"Shader index is too large"}
        return index.readLines().map { path ->
            ShaderPackage.entryPath(path)
            require(path.endsWith(".slangp")){"Invalid preset in shader index"}
            val known=featured[path]
            val id=known?.first ?: "library_"+MessageDigest.getInstance("SHA-256").digest(path.toByteArray()).take(12).joinToString(""){"%02x".format(it)}
            ShaderChoice(id,known?.second ?: path.substringBeforeLast('.'),"library/$revision/$path")
        }
    }
    fun install(archive:File,root:File,revision:String,checkCancelled:()->Unit={}):File {
        revision(revision)
        require(archive.length() in 1..MAX_ARCHIVE){"Shader archive exceeds 128 MiB"}
        root.mkdirs()
        val destination=File(root,revision)
        if(File(destination,".bfi-index").isFile)return destination
        val staging=File(root,"pending-$revision").apply{deleteRecursively();mkdirs()}
        try {
            ZipFile(archive).use { zip ->
                val names=hashSetOf<String>();val presets=mutableListOf<String>();var total=0L;var count=0
                val iterator=zip.entries();val prefix="slang-shaders-$revision/"
                val buffer=ByteArray(65536)
                while(iterator.hasMoreElements()) {
                    checkCancelled()
                    require(++count<=50_000){"Shader archive has too many entries"}
                    val entry=iterator.nextElement()
                    require(entry.name.startsWith(prefix)){"Unexpected source archive root"}
                    val path=entry.name.removePrefix(prefix).trimEnd('/')
                    if(path.isEmpty())continue
                    ShaderPackage.entryPath(path)
                    require(!path.startsWith(".bfi-") && names.add(path)){"Duplicate or reserved archive path"}
                    if(entry.isDirectory)continue
                    require(entry.size in 0..MAX_FILE){"Shader source file exceeds 32 MiB"}
                    val output=File(staging,path).apply{parentFile?.mkdirs()}
                    var size=0L;val crc=CRC32()
                    zip.getInputStream(entry).use { input -> output.outputStream().use { out ->
                        while(true) {
                            checkCancelled();val n=input.read(buffer);if(n<0)break
                            size+=n;total+=n
                            require(size<=MAX_FILE && total<=MAX_EXPANDED){"Shader library exceeds its storage budget"}
                            crc.update(buffer,0,n);out.write(buffer,0,n)
                        }
                    } }
                    require(size==entry.size && crc.value==entry.crc){"Damaged shader source file: $path"}
                    if(path.endsWith(".slangp"))presets+=path
                }
                require(presets.isNotEmpty()){"Source archive contains no presets"}
                File(staging,".bfi-index").writeText(presets.sorted().joinToString("\n"))
            }
            checkCancelled()
            check(staging.renameTo(destination)){"Could not install shader library"}
            return destination
        }finally{staging.deleteRecursively()}
    }
    fun open(root:File,path:String):InputStream {
        val parts=path.split('/',limit=3)
        require(parts.size==3 && parts[0]=="library"){"Invalid shader library path"}
        revision(parts[1]);ShaderPackage.entryPath(parts[2])
        val file=File(root,"${parts[1]}/${parts[2]}")
        require(file.length()<=MAX_FILE){"Shader dependency exceeds 32 MiB"}
        return file.inputStream()
    }
    val featured=mapOf(
        "crt/crt-mattias.slangp" to ("mattias" to "Mattias CRT"),
        "crt/crt-hyllian-ntsc-rainbow.slangp" to ("hyllian_ntsc_rainbow" to "CRT Hyllian NTSC Rainbow"),
        "crt/zfast-crt-composite.slangp" to ("zfast" to "zfast CRT Composite"),
        "crt/newpixie-crt.slangp" to ("newpixie" to "Newpixie CRT"),
        "crt/crt-geom.slangp" to ("geom" to "CRT Geom"),
        "crt/crt-geom-deluxe.slangp" to ("geom_deluxe" to "CRT Geom Deluxe"),
        "crt/crt-lottes.slangp" to ("lottes" to "CRT Lottes"),
        "vhs/vhs_and_crt_godot.slangp" to ("vhs_godot" to "VHS and CRT Godot")
    )
}
