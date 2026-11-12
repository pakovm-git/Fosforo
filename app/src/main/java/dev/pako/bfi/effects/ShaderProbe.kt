package dev.pako.bfi.effects

import android.content.Context
import android.opengl.EGL14.*
import android.opengl.GLES30.*

/** Offscreen compile/link plus two real pipeline draws. No display capture or video files. */
internal class ShaderProbe(private val context:Context):AutoCloseable {
    private val display=eglGetDisplay(EGL_DEFAULT_DISPLAY)
    private var eglContext=EGL_NO_CONTEXT
    private var surface=EGL_NO_SURFACE
    private var texture=0
    private val failedPasses=hashMapOf<String,String>()
    private val width=context.resources.displayMetrics.widthPixels.coerceAtLeast(1)
    private val height=context.resources.displayMetrics.heightPixels.coerceAtLeast(1)
    init {
        try {
            check(eglInitialize(display,IntArray(2),0,IntArray(2),0)){"Could not initialize compatibility checker"}
            val configs=arrayOfNulls<android.opengl.EGLConfig>(1);val count=IntArray(1)
            check(eglChooseConfig(display,intArrayOf(EGL_RENDERABLE_TYPE,0x40,EGL_SURFACE_TYPE,EGL_PBUFFER_BIT,EGL_RED_SIZE,8,EGL_GREEN_SIZE,8,EGL_BLUE_SIZE,8,EGL_NONE),0,configs,0,1,count,0) && count[0]>0){"No offscreen OpenGL ES 3 configuration"}
            eglContext=eglCreateContext(display,configs[0],EGL_NO_CONTEXT,intArrayOf(EGL_CONTEXT_CLIENT_VERSION,3,EGL_NONE),0)
            check(eglContext!=EGL_NO_CONTEXT){"Could not create compatibility context"}
            surface=eglCreatePbufferSurface(display,configs[0],intArrayOf(EGL_WIDTH,1,EGL_HEIGHT,1,EGL_NONE),0)
            check(surface!=EGL_NO_SURFACE && eglMakeCurrent(display,surface,surface,eglContext)){"Could not activate compatibility context"}
            val ids=IntArray(1);glGenTextures(1,ids,0);texture=ids[0]
            glBindTexture(GL_TEXTURE_2D,texture);glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,width,height,0,GL_RGBA,GL_UNSIGNED_BYTE,null)
            check(glGetError()==GL_NO_ERROR){"Could not allocate compatibility source"}
        }catch(error:Exception){close();throw error}
    }
    fun check(definition:ShaderDefinition,open:(String)->java.io.InputStream={ShaderCatalog.open(context,it)}) {
        repeat(8){glGetError()}
        definition.passes.firstOrNull{it.path in failedPasses}?.let{error(failedPasses.getValue(it.path))}
        val pipeline=try{ShaderPipeline(context,definition,width,height,width,height,open)}catch(error:Exception){
            generateSequence<Throwable>(error){it.cause}.filterIsInstance<ShaderPassFailure>().firstOrNull()?.let{failedPasses[it.path]=it.message.orEmpty()}
            throw error
        }
        try {
            repeat(2){pipeline.render(texture,it,ShaderState(definition.choice.id,emptyMap()))}
            glFinish()
        }finally{pipeline.release()}
    }
    override fun close() {
        if(texture!=0){glDeleteTextures(1,intArrayOf(texture),0);texture=0}
        eglMakeCurrent(display,EGL_NO_SURFACE,EGL_NO_SURFACE,EGL_NO_CONTEXT)
        if(surface!=EGL_NO_SURFACE){eglDestroySurface(display,surface);surface=EGL_NO_SURFACE}
        if(eglContext!=EGL_NO_CONTEXT){eglDestroyContext(display,eglContext);eglContext=EGL_NO_CONTEXT}
        // The default display is shared with the live renderer; destroy only our resources.
        eglReleaseThread()
    }
}
