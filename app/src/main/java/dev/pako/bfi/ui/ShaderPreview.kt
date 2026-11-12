package dev.pako.bfi.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.opengl.EGL14.*
import android.opengl.GLES30.*
import android.opengl.GLSurfaceView
import android.os.SystemClock
import android.util.LruCache
import dev.pako.bfi.effects.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.concurrent.Executors
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** Synthetic bouncing-ball source through the production shader pipeline; never captures the display. */
internal class ShaderPreviewRenderer(private val context:Context,initial:ShaderState,private val failed:(String)->Unit):GLSurfaceView.Renderer {
    @Volatile var state=initial
    @Volatile var useShader=true
    @Volatile var effectsEnabled=true
    @Volatile var blackPattern:BfiPattern?=null
    @Volatile var hz=120.0
    @Volatile var frozenTime:Float?=null
    private val grid=BlueprintDrawable(context)
    private var gridBitmap:Bitmap?=null
    private var gridCanvas:Canvas?=null
    private var gridTexture=0
    private var lastGrid=Long.MIN_VALUE
    private var pipeline:ShaderPipeline?=null
    private var loaded:String?=null
    private var ballProgram=0
    private var copyProgram=0
    private var source=0
    private var sourceFbo=0
    private var width=1
    private var height=1
    private var frame=0
    private var broken=false
    private val vertices=ByteBuffer.allocateDirect(64).order(ByteOrder.nativeOrder()).asFloatBuffer().apply{put(floatArrayOf(-1f,-1f,0f,0f,1f,-1f,1f,0f,-1f,1f,0f,1f,1f,1f,1f,1f));position(0)}
    override fun onSurfaceCreated(gl:GL10?,config:EGLConfig?){try{initialize()}catch(error:Exception){broken=true;failed(error.message?:"Preview initialization failed")}}
    override fun onSurfaceChanged(gl:GL10?,w:Int,h:Int){if(!broken)try{resize(w,h)}catch(error:Exception){broken=true;failed(error.message?:"Preview resize failed")}}
    override fun onDrawFrame(gl:GL10?){
        if(broken)return
        try{render(frozenTime?:(SystemClock.elapsedRealtime()%60000)/1000f)}catch(error:Exception){broken=true;failed(error.message?:"Shader preview failed")}
    }
    fun initialize(){
        pipeline=null;loaded=null;frame=0;broken=false;gridBitmap?.recycle();gridBitmap=null;gridCanvas=null;lastGrid=Long.MIN_VALUE
        val vertex="""#version 300 es
            in vec2 position; in vec2 texcoord; out vec2 uv;
            void main(){uv=texcoord;gl_Position=vec4(position,0.,1.);}
        """.trimIndent()
        ballProgram=link(vertex,"""#version 300 es
            precision highp float; in vec2 uv; uniform float time; uniform vec2 size; uniform sampler2D backdrop; out vec4 color;
            void main(){
                vec3 c=texture(backdrop,vec2(uv.x,1.-uv.y)).rgb;
                vec2 ball=vec2(.5+.35*sin(time*2.),.5+.2*cos(time*3.));
                if(length((uv-ball)*size)<.045*min(size.x,size.y))c=vec3(1.,.73,.27);
                if(uv.y>.09&&uv.y<.12)c=vec3(1.,.502,0.);
                if(uv.y>.02&&uv.y<.08){float cell=floor(uv.x*32.);float bit=mod(floor(floor(time*1000./16.)/exp2(mod(cell,16.))),2.);if(cell>=16.)bit=1.-bit;c=vec3(bit);}
                color=vec4(c,1.);
            }
        """.trimIndent())
        copyProgram=link(vertex,"""#version 300 es
            precision highp float; in vec2 uv; uniform sampler2D image; out vec4 color;
            void main(){color=texture(image,uv);}
        """.trimIndent())
        val ids=IntArray(1);glGenTextures(1,ids,0);source=ids[0];glBindTexture(GL_TEXTURE_2D,source)
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE)
        glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,1,1,0,GL_RGBA,GL_UNSIGNED_BYTE,null)
        glGenFramebuffers(1,ids,0);sourceFbo=ids[0];glBindFramebuffer(GL_FRAMEBUFFER,sourceFbo)
        glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,source,0)
        check(glCheckFramebufferStatus(GL_FRAMEBUFFER)==GL_FRAMEBUFFER_COMPLETE){"Preview source framebuffer unavailable"}
        glGenTextures(1,ids,0);gridTexture=ids[0];glBindTexture(GL_TEXTURE_2D,gridTexture)
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE)
    }
    fun resize(w:Int,h:Int){
        width=w.coerceAtLeast(1);height=h.coerceAtLeast(1);pipeline?.release();pipeline=null;loaded=null
        glBindTexture(GL_TEXTURE_2D,source);glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,width,height,0,GL_RGBA,GL_UNSIGNED_BYTE,null)
        gridBitmap?.recycle();gridBitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);gridCanvas=Canvas(gridBitmap!!);grid.setBounds(0,0,width,height);lastGrid=Long.MIN_VALUE
    }
    fun render(time:Float){
        val current=state
        val tick=frozenTime?.let{(it*1000).toLong()}?:SystemClock.uptimeMillis()
        if(lastGrid==Long.MIN_VALUE||tick-lastGrid>=50){
            gridCanvas?.let{grid.drawScene(it,tick)};glBindTexture(GL_TEXTURE_2D,gridTexture);gridBitmap?.let{android.opengl.GLUtils.texImage2D(GL_TEXTURE_2D,0,it,0)};lastGrid=tick
        }
        glBindFramebuffer(GL_FRAMEBUFFER,sourceFbo);glViewport(0,0,width,height);glDisable(GL_BLEND);glDisable(GL_SCISSOR_TEST)
        glUseProgram(ballProgram);glUniform1f(glGetUniformLocation(ballProgram,"time"),time);glUniform2f(glGetUniformLocation(ballProgram,"size"),width.toFloat(),height.toFloat());glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,gridTexture);glUniform1i(glGetUniformLocation(ballProgram,"backdrop"),0);quad(ballProgram)
        val texture=if(useShader&&effectsEnabled){
            if(loaded!=current.id){pipeline?.release();pipeline=null;loaded=null;pipeline=ShaderPipeline(context,ShaderCatalog.load(context,current.id),width,height,width,height);loaded=current.id}
            pipeline!!.render(source,frame,current)
        }else source
        glBindFramebuffer(GL_FRAMEBUFFER,0);glViewport(0,0,width,height)
        val black=effectsEnabled&&blackPattern?.let{blackSlot(System.nanoTime(),0,hz,it)}==true
        if(black){glClearColor(0f,0f,0f,1f);glClear(GL_COLOR_BUFFER_BIT)}else{
        glUseProgram(copyProgram);glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,texture);glUniform1i(glGetUniformLocation(copyProgram,"image"),0);quad(copyProgram)}
        check(glGetError()==GL_NO_ERROR){"Shader preview draw failed"};frame++
    }
    private fun quad(program:Int){
        glBindBuffer(GL_ARRAY_BUFFER,0)
        for((name,offset) in listOf("position" to 0,"texcoord" to 2)){
            val attr=glGetAttribLocation(program,name);if(attr>=0){vertices.position(offset);glEnableVertexAttribArray(attr);glVertexAttribPointer(attr,2,GL_FLOAT,false,16,vertices)}
        }
        glDrawArrays(GL_TRIANGLE_STRIP,0,4)
    }
    private fun link(vertex:String,fragment:String):Int{
        fun compile(type:Int,source:String):Int{val id=glCreateShader(type);glShaderSource(id,source);glCompileShader(id);val ok=IntArray(1);glGetShaderiv(id,GL_COMPILE_STATUS,ok,0);if(ok[0]==0){val message=glGetShaderInfoLog(id);glDeleteShader(id);error(message)};return id}
        val v=compile(GL_VERTEX_SHADER,vertex);val f=try{compile(GL_FRAGMENT_SHADER,fragment)}catch(e:Exception){glDeleteShader(v);throw e}
        val program=glCreateProgram();glAttachShader(program,v);glAttachShader(program,f);glLinkProgram(program);glDeleteShader(v);glDeleteShader(f)
        val ok=IntArray(1);glGetProgramiv(program,GL_LINK_STATUS,ok,0);if(ok[0]==0){val message=glGetProgramInfoLog(program);glDeleteProgram(program);error(message)};return program
    }
    fun release(){gridBitmap?.recycle();gridBitmap=null;if(gridTexture!=0)glDeleteTextures(1,intArrayOf(gridTexture),0);pipeline?.release();pipeline=null;if(source!=0)glDeleteTextures(1,intArrayOf(source),0);if(sourceFbo!=0)glDeleteFramebuffers(1,intArrayOf(sourceFbo),0);if(ballProgram!=0)glDeleteProgram(ballProgram);if(copyProgram!=0)glDeleteProgram(copyProgram)}
}

/** Bounded still-image cache. It renders only synthetic pixels, with one worker and no videos. */
internal class ShaderThumbnails(private val context:Context,private val active:()->Boolean):AutoCloseable {
    private val worker=Executors.newSingleThreadExecutor()
    private val pending=mutableMapOf<String,MutableList<(Bitmap?)->Unit>>()
    private val memory=object:LruCache<String,Bitmap>(8*1024*1024){override fun sizeOf(key:String,value:Bitmap)=value.allocationByteCount}
    private val folder=File(context.cacheDir,"shader-previews-v2").apply{mkdirs()}
    fun load(choice:ShaderChoice,ready:(Bitmap?)->Unit){
        val key=MessageDigest.getInstance("SHA-256").digest((choice.id+":"+choice.assetPath).toByteArray()).joinToString(""){"%02x".format(it)}
        memory.get(key)?.let{ready(it);return}
        pending[key]?.let{if(it.size<16)it.add(ready);return}
        if(pending.size>=12||worker.isShutdown)return
        pending[key]=mutableListOf(ready)
        worker.execute{
            val bitmap=if(active())try{
                val file=File(folder,"$key.png")
                android.graphics.BitmapFactory.decodeFile(file.path)?:render(choice).also{image->
                    file.outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)}
                    folder.listFiles()?.sortedByDescending{it.lastModified()}?.drop(256)?.forEach{it.delete()}
                }
            }catch(_:Exception){null}else null
            android.os.Handler(android.os.Looper.getMainLooper()).post{val callbacks=pending.remove(key).orEmpty();if(bitmap!=null)memory.put(key,bitmap);if(active())callbacks.forEach{it(bitmap)}}
        }
    }
    private fun render(choice:ShaderChoice):Bitmap {
        val display=eglGetDisplay(EGL_DEFAULT_DISPLAY);var eglContext=EGL_NO_CONTEXT;var surface=EGL_NO_SURFACE;var renderer:ShaderPreviewRenderer?=null
        try{
            check(eglInitialize(display,IntArray(2),0,IntArray(2),0))
            val configs=arrayOfNulls<android.opengl.EGLConfig>(1);val count=IntArray(1)
            check(eglChooseConfig(display,intArrayOf(EGL_RENDERABLE_TYPE,0x40,EGL_SURFACE_TYPE,EGL_PBUFFER_BIT,EGL_RED_SIZE,8,EGL_GREEN_SIZE,8,EGL_BLUE_SIZE,8,EGL_ALPHA_SIZE,8,EGL_NONE),0,configs,0,1,count,0)&&count[0]>0)
            eglContext=eglCreateContext(display,configs[0],EGL_NO_CONTEXT,intArrayOf(EGL_CONTEXT_CLIENT_VERSION,3,EGL_NONE),0);check(eglContext!=EGL_NO_CONTEXT)
            surface=eglCreatePbufferSurface(display,configs[0],intArrayOf(EGL_WIDTH,160,EGL_HEIGHT,120,EGL_NONE),0);check(surface!=EGL_NO_SURFACE&&eglMakeCurrent(display,surface,surface,eglContext))
            renderer=ShaderPreviewRenderer(context,ShaderState(choice.id,emptyMap())){}
            renderer.initialize();renderer.resize(160,120);repeat(6){renderer.render(1.2f)}
            val pixels=ByteBuffer.allocateDirect(160*120*4);glReadPixels(0,0,160,120,GL_RGBA,GL_UNSIGNED_BYTE,pixels);check(glGetError()==GL_NO_ERROR)
            val colors=IntArray(160*120)
            for(y in 0 until 120)for(x in 0 until 160){val i=(y*160+x)*4;colors[(119-y)*160+x]=((pixels.get(i+3).toInt() and 255) shl 24) or ((pixels.get(i).toInt() and 255) shl 16) or ((pixels.get(i+1).toInt() and 255) shl 8) or (pixels.get(i+2).toInt() and 255)}
            return Bitmap.createBitmap(colors,160,120,Bitmap.Config.ARGB_8888)
        }finally{
            if(eglContext!=EGL_NO_CONTEXT){renderer?.release();eglMakeCurrent(display,EGL_NO_SURFACE,EGL_NO_SURFACE,EGL_NO_CONTEXT)}
            if(surface!=EGL_NO_SURFACE)eglDestroySurface(display,surface);if(eglContext!=EGL_NO_CONTEXT)eglDestroyContext(display,eglContext)
            // The default display is shared with live output; never terminate it.
            eglReleaseThread()
        }
    }
    override fun close(){worker.shutdownNow();memory.evictAll()}
}
