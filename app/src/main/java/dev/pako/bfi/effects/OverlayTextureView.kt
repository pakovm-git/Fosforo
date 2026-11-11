package dev.pako.bfi.effects

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import android.view.TextureView
import java.util.concurrent.atomic.AtomicBoolean

/** Render inside the trusted accessibility window; SurfaceView adds an untrusted input-occluding layer on the Nova. */
class OverlayTextureView(context: Context, val engine: EffectRenderer, private val ready:(OverlayTextureView)->Unit={}) : TextureView(context), TextureView.SurfaceTextureListener {
    private val thread=HandlerThread("Shader output").apply{start()}
    private val gl=Handler(thread.looper)
    private val renderQueued=AtomicBoolean(false)
    internal val cadence=RenderCadence()
    @Volatile private var closing=false
    private var eglDisplay: EGLDisplay?=null
    private var eglContext: EGLContext?=null
    private var eglSurface: EGLSurface?=null
    private var output: Surface?=null
    private var ownedTexture: SurfaceTexture?=null
    init { isOpaque=false;isClickable=false;isFocusable=false;surfaceTextureListener=this }
    override fun onSurfaceTextureAvailable(texture: SurfaceTexture,width: Int,height: Int) {
        ownedTexture=texture
        try { ready(this) }
        catch(error:Exception) { engine.reportFailure("Output setup failed: ${error.message}");return }
        gl.post {
            if(closing)return@post
            try {
                val d=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
                check(d!=EGL14.EGL_NO_DISPLAY){"No EGL display"};eglDisplay=d
                check(EGL14.eglInitialize(d,IntArray(2),0,IntArray(2),0)){"EGL initialization failed"}
                val attrs=intArrayOf(EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,
                    EGL14.EGL_RENDERABLE_TYPE,0x40,EGL14.EGL_SURFACE_TYPE,EGL14.EGL_WINDOW_BIT,EGL14.EGL_NONE)
                val configs=arrayOfNulls<EGLConfig>(1);val count=IntArray(1)
                check(EGL14.eglChooseConfig(d,attrs,0,configs,0,1,count,0)&&count[0]>0){"No translucent EGL config"}
                val c=EGL14.eglCreateContext(d,configs[0],EGL14.EGL_NO_CONTEXT,intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION,3,EGL14.EGL_NONE),0)
                check(c!=EGL14.EGL_NO_CONTEXT){"EGL context failed"};eglContext=c
                output=Surface(texture)
                val s=EGL14.eglCreateWindowSurface(d,configs[0],output,intArrayOf(EGL14.EGL_NONE),0)
                check(s!=EGL14.EGL_NO_SURFACE){"EGL surface failed"};eglSurface=s
                check(EGL14.eglMakeCurrent(d,s,s,c)){"EGL makeCurrent failed"}
                engine.onSurfaceCreated(null,null);engine.onSurfaceChanged(null,width,height)
                draw()
            } catch(e: Exception) { engine.reportFailure("Output setup failed: ${e.message}") }
        }
    }
    fun requestRender(frame:Long=engine.now,presentation:Long=0,slot:Long=-1) {
        val request=System.nanoTime()
        if(closing)return
        if(!renderQueued.compareAndSet(false,true)){cadence.dropped();return}
        gl.post { renderQueued.set(false);if(!closing){engine.now=frame;engine.presentationSlot=slot;engine.presentationTarget=presentation;draw(frame,request)} }
    }
    private fun draw(requestedFrame:Long=0,requestedAt:Long=0) {
        val d=eglDisplay?:return;val s=eglSurface?:return
        try {
            val consumed=engine.now;val began=System.nanoTime()
            engine.onDrawFrame(null)
            check(EGL14.eglSwapBuffers(d,s)){"Output swap failed"}
            cadence.record(requestedFrame,consumed,requestedAt,began,System.nanoTime(),engine.lastBlack,engine.presentationTarget)
        } catch(e: Exception){engine.reportFailure("Output stopped: ${e.message}")}
    }
    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture,width: Int,height: Int) {
        gl.post { if(!closing)engine.onSurfaceChanged(null,width,height) }
    }
    override fun onSurfaceTextureUpdated(texture: SurfaceTexture){cadence.updated(System.nanoTime())}
    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean { close();return false }
    fun close() {
        if(closing)return
        closing=true
        gl.post {
            try { if(eglContext!=null)engine.release() }
            finally {
                eglDisplay?.let { d ->
                    EGL14.eglMakeCurrent(d,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT)
                    eglSurface?.let{EGL14.eglDestroySurface(d,it)}
                    eglContext?.let{EGL14.eglDestroyContext(d,it)}
                    EGL14.eglTerminate(d)
                }
                output?.release();output=null
                ownedTexture?.release();ownedTexture=null
                eglSurface=null;eglContext=null;eglDisplay=null
                thread.quitSafely()
            }
        }
    }
}
