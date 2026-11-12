package dev.pako.bfi.effects

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.HttpsURLConnection
import java.net.URL

/** Explicit source downloads only; captures and settings are never sent to the network. */
object ShaderLibrary {
    data class Status(val busy:Boolean=false,val message:String="",val revision:String?=null,val report:List<String>?=null)
    private val worker=Executors.newSingleThreadExecutor()
    private val main=Handler(Looper.getMainLooper())
    private val cancelled=AtomicBoolean()
    private val observers=linkedSetOf<()->Unit>()
    var status=Status();private set
    private var cachedRevision:String?=null
    private var cachedChoices=emptyList<ShaderChoice>()
    private fun root(context:Context)=File(context.filesDir,"shader-library")
    private fun current(context:Context):String? = runCatching {
        ShaderLibraryArchive.revision(String(AtomicFile(File(root(context),"current")).readFully()).trim())
    }.getOrNull()
    fun choices(context:Context):List<ShaderChoice> {
        val revision=current(context)?:return emptyList()
        if(cachedRevision!=revision) {
            cachedChoices=runCatching{ShaderLibraryArchive.choices(File(root(context),revision),revision)}.getOrDefault(emptyList())
            cachedRevision=revision
        }
        return cachedChoices
    }
    fun open(context:Context,path:String)=ShaderLibraryArchive.open(root(context),path)
    fun subscribe(observer:()->Unit):()->Unit {
        observers+=observer;observer()
        return {observers-=observer}
    }
    private fun publish(next:Status) {status=next;observers.toList().forEach{it()}}
    fun cancel(){cancelled.set(true)}
    private fun checkCancelled(){if(cancelled.get())throw CancellationException("Download canceled")}
    private fun connection(url:String):HttpsURLConnection {
        val address=URL(url)
        require(address.protocol=="https" && address.host in setOf("api.github.com","codeload.github.com")){"Invalid shader source host"}
        val connection=(address.openConnection() as HttpsURLConnection).apply {
            instanceFollowRedirects=false;connectTimeout=20_000;readTimeout=20_000
            setRequestProperty("User-Agent","BFI-shader-library")
        }
        try {if(connection.responseCode!=200)throw IOException("GitHub returned HTTP ${connection.responseCode}. Try again later.")}
        catch(error:Exception){connection.disconnect();throw error}
        return connection
    }
    fun report(context:Context):List<String> = runCatching {
        val revision=current(context)?:return emptyList()
        val file=File(root(context),"$revision/.bfi-unavailable.json")
        require(file.length()<=4*1024*1024)
        val data=org.json.JSONArray(file.readText())
        (0 until data.length()).map{data.getString(it)}
    }.getOrDefault(emptyList())
    private fun validate(context:Context,revision:String):List<String> {
        val folder=File(root(context),revision)
        val all=ShaderLibraryArchive.choices(folder,revision,false)
        val accepted=mutableListOf<String>();val failed=mutableListOf<String>()
        val parameterChecks=hashMapOf<String,String?>()
        ShaderProbe(context).use { probe ->
            all.forEachIndexed { index,choice ->
                checkCancelled()
                main.post{publish(Status(true,"Checking shaders: ${index+1}/${all.size} — ${choice.label}"))}
                try {
                    var budget=8*1024*1024
                    val definition=ShaderCatalog.loadChoice(choice){path->
                        val bytes=ShaderCatalog.open(context,path).use{it.readNBytesBounded(2*1024*1024)}
                        budget-=bytes.size;require(budget>=0){"Shader sources expand beyond 8 MiB"}
                        val text=bytes.toString(Charsets.UTF_8)
                        if(path !in parameterChecks)parameterChecks[path]=try{ShaderCatalog.parseParameters(text);null}catch(error:Exception){error.message?:"Invalid shader parameters"}
                        parameterChecks[path]?.let{error(it)}
                        text
                    }
                    probe.check(definition)
                    accepted+=choice.assetPath.split('/',limit=3)[2]
                }catch(error:Exception){failed+="${choice.label}\n${error.message.orEmpty().take(400)}"}
            }
        }
        checkCancelled()
        File(folder,".bfi-unavailable.json").writeText(org.json.JSONArray(failed).toString())
        val active=AtomicFile(File(folder,".bfi-compatible"));val output=active.startWrite()
        try{output.write(accepted.joinToString("\n").toByteArray());active.finishWrite(output)}catch(error:Exception){active.failWrite(output);throw error}
        return failed
    }
    fun scan(context:Context) {
        if(status.busy)return
        val app=context.applicationContext;val revision=current(app)?:return
        cancelled.set(false);publish(Status(true,"Checking installed shaders…"))
        worker.execute {
            try {
                val failed=validate(app,revision)
                main.post{cachedRevision=null;publish(Status(false,"Compatibility check finished: ${failed.size} unavailable.",revision,failed))}
            }catch(error:Exception){main.post{publish(Status(false,error.message?:"Compatibility check failed"))}}
        }
    }
    fun removeAll(context:Context) {
        if(status.busy)return
        val app=context.applicationContext
        publish(Status(true,"Removing downloaded and imported shaders…"))
        worker.execute {
            val message=try {
                val folder=root(app)
                check(!folder.exists() || folder.deleteRecursively()){"Could not remove downloaded shaders"}
                ShaderImports.deleteAll(app)
                "Downloaded and imported shaders removed. Bundled shaders kept."
            }catch(error:Exception){error.message?:"Could not remove shaders"}
            main.post {cachedRevision=null;cachedChoices=emptyList();publish(Status(false,message,""))}
        }
    }
    fun download(context:Context) {
        if(status.busy)return
        val app=context.applicationContext
        cancelled.set(false);publish(Status(true,"Checking Libretro source…"))
        worker.execute {
            val file=File(root(app).apply{mkdirs()},"download.zip.part")
            try {
                require(root(app).usableSpace>=150L*1024*1024){"Free at least 150 MiB before downloading the shader library"}
                val api=connection("https://api.github.com/repos/libretro/slang-shaders/git/ref/heads/master")
                val revision=try {
                    val body=api.inputStream.use{it.readNBytesBounded(65536)}.toString(Charsets.UTF_8)
                    ShaderLibraryArchive.revision(JSONObject(body).getJSONObject("object").getString("sha"))
                }finally{api.disconnect()}
                checkCancelled()
                val alreadyInstalled=File(root(app),"$revision/.bfi-index").isFile
                if(!alreadyInstalled) {
                    val source=connection("https://codeload.github.com/libretro/slang-shaders/zip/$revision")
                    try {
                        require(source.contentLengthLong<=ShaderLibraryArchive.MAX_ARCHIVE){"Shader archive exceeds 128 MiB"}
                        source.inputStream.use{input->file.outputStream().use{output->
                            val buffer=ByteArray(65536);var total=0L;var reported=-1L
                            while(true) {
                                checkCancelled();val n=input.read(buffer);if(n<0)break
                                total+=n;require(total<=ShaderLibraryArchive.MAX_ARCHIVE){"Shader archive exceeds 128 MiB"}
                                output.write(buffer,0,n)
                                val mib=total/(1024*1024)
                                if(mib!=reported){reported=mib;main.post{publish(Status(true,"Downloading Libretro: $mib MiB…"))}}
                            }
                        }}
                    }finally{source.disconnect()}
                    main.post{publish(Status(true,"Organizing shader categories…"))}
                    ShaderLibraryArchive.install(file,root(app),revision,::checkCancelled)
                }
                checkCancelled()
                val failed=if(!File(root(app),"$revision/.bfi-compatible").isFile)validate(app,revision) else report(app)
                val catalog=ShaderLibraryArchive.choices(File(root(app),revision),revision)
                val active=AtomicFile(File(root(app),"current"));val output=active.startWrite()
                try {output.write(revision.toByteArray());active.finishWrite(output)}
                catch(error:Exception){active.failWrite(output);throw error}
                main.post {
                    cachedRevision=revision;cachedChoices=catalog
                    val message=if(alreadyInstalled)"Libretro library is up to date (${catalog.size} presets)." else "Downloaded ${catalog.size} presets in ${catalog.map{it.category}.distinct().size} categories."
                    publish(Status(false,message+" ${failed.size} unavailable.",revision,failed))
                }
            }catch(error:Exception) {
                main.post{publish(Status(false,error.message?:"Could not download the shader library"))}
            }finally{file.delete()}
        }
    }
}

internal fun java.io.InputStream.readNBytesBounded(limit:Int):ByteArray {
    val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
    while(true){val n=read(buffer);if(n<0)break;require(output.size()+n<=limit){"Shader text exceeds its size limit"};output.write(buffer,0,n)}
    return output.toByteArray()
}
