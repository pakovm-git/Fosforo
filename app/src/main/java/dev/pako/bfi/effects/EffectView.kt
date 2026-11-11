package dev.pako.bfi.effects

import android.content.Context
import android.graphics.*
import android.opengl.GLES20.*
import android.opengl.GLES11Ext
import android.opengl.GLUtils
import android.opengl.GLSurfaceView
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*

class EffectView(context: Context, val engine: EffectRenderer) : GLSurfaceView(context) {
    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8, 8, 8, 8, 0, 0)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        setZOrderOnTop(true)
        setRenderer(engine)
        renderMode = RENDERMODE_WHEN_DIRTY
        contentDescription = "Animated CRT comparison preview"
    }
    fun close() { queueEvent { engine.release() }; onPause() }
}

class EffectRenderer(
    private val context: Context,
    private val preview: Boolean = false,
    private val inputReady: (Surface) -> Unit = {},
    private val sample: (Boolean, Int?) -> Unit = { _, _ -> },
    private val failure: (String) -> Unit = {},
    private val externalFrames: Boolean = false,
    private val processed: (Double) -> Unit = {},
) : GLSurfaceView.Renderer {
    private data class Frame(val data:ByteBuffer,val width:Int,val height:Int)
    private val submitted=AtomicReference<Frame?>(null)
    private var resultPixels:ByteBuffer?=null
    private var processedFrames=0
    private var measuredDelta=0.0
    fun submitFrame(data:ByteBuffer,width:Int,height:Int){submitted.set(Frame(data,width,height))}
    @Volatile var phase = "baseline" // baseline is transparent; probe, shader and bfi are output modes.
    @Volatile var diagnosticMarkers = false
    var presentationSlot = -1L
    var presentationTarget = 0L
    @Volatile var now = 0L
    @Volatile var bfiInOutput=true
    @Volatile var lastBlack=false;private set
    @Volatile var hz = 120.0
    private val vertices = ByteBuffer.allocateDirect(16 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(floatArrayOf(-1f,-1f,0f,0f, 1f,-1f,1f,0f, -1f,1f,0f,1f, 1f,1f,1f,1f)); position(0)
    }
    private var outputW = 1; private var outputH = 1
    private var sourceW = 640; private var sourceH = 360
    private var processW = 480; private var processH = 270
    private var copy = 0; private var oesCopy = 0
    private var pipeline:ShaderPipeline?=null
    private var rejectedRender:ShaderState?=null
    private var appliedState:ShaderState?=null
    private var sourceTex = 0; private var sourceFbo = 0
    private var externalTex = 0; private var input: android.graphics.SurfaceTexture? = null
    private var inputSurface: Surface? = null
    private val pending = AtomicBoolean(false)
    private val transform = FloatArray(16)
    private var started = 0L; private var lastEffect = 0L; private var lastScene = 0L; private var lastRead = 0L
    private var totalFrames = 0; private var measureStart = 0L
    private var pixels: ByteBuffer? = null
    private val bitmap by lazy { Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888) }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var broken = false
    private var haveFrame = false
    @Volatile var lastCaptureAt = 0L; private set
    fun captureSize() = sourceW to sourceH
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        try {
            started = System.nanoTime(); measureStart = started
            if (phase == "bfi") return
            val major=IntArray(1);val minor=IntArray(1)
            android.opengl.GLES30.glGetIntegerv(android.opengl.GLES30.GL_MAJOR_VERSION,major,0)
            android.opengl.GLES30.glGetIntegerv(android.opengl.GLES30.GL_MINOR_VERSION,minor,0)
            check(major[0]>3 || major[0]==3 && minor[0]>=1){"OpenGL ES 3.1 is required"}
            copy = program(VERTEX, FRAGMENT)
            oesCopy = program(VERTEX_OES, FRAGMENT_OES)
            started = System.nanoTime(); measureStart = started
            if (!preview && !externalFrames) {
                externalTex = texture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES)
                input = android.graphics.SurfaceTexture(externalTex).also { st ->
                    st.setOnFrameAvailableListener { pending.set(true) }
                    inputSurface = Surface(st)
                }
            }
        } catch (e: Exception) { fail("GPU setup failed: ${e.message}") }
    }
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        if (broken) return
        outputW = width; outputH = height
        if (phase == "bfi") return
        val size=renderDimensions(width,height,preview)
        sourceW=size.sourceW;sourceH=size.sourceH;processW=size.viewportW;processH=size.viewportH
        android.util.Log.i("ShaderGPU","Render dimensions: source=${sourceW}x$sourceH viewport=${processW}x$processH output=${outputW}x$outputH preview=$preview")
        destroyTargets()
        sourceTex = texture(GL_TEXTURE_2D); sourceFbo = target(sourceTex, sourceW, sourceH)
        pixels = if(!preview && !externalFrames)ByteBuffer.allocateDirect(sourceW * sourceH * 4) else null
        resultPixels=null
        input?.setDefaultBufferSize(sourceW, sourceH)
        if (!preview) inputSurface?.let(inputReady)
    }
    override fun onDrawFrame(gl: GL10?) {
        if (broken) return
        try { draw() } catch (e: Exception) { fail("Renderer stopped: ${e.message}") }
    }
    private fun draw() {
        val tick = now.takeIf { it > 0 } ?: System.nanoTime()
        val frame=if(externalFrames)submitted.getAndSet(null) else null
        val fresh = if (preview) tick - lastScene > 33_000_000 else if(externalFrames)frame!=null else pending.getAndSet(false)
        if (fresh) {
            if (preview) {uploadScene(tick);lastScene=tick} else if(externalFrames) {
                check(frame!!.width==sourceW && frame.height==sourceH){"Capture dimensions changed"}
                glBindTexture(GL_TEXTURE_2D,sourceTex)
                glTexSubImage2D(GL_TEXTURE_2D,0,0,0,sourceW,sourceH,GL_RGBA,GL_UNSIGNED_BYTE,frame.data)
                lastCaptureAt=System.nanoTime()
            } else {
                input!!.updateTexImage(); input!!.getTransformMatrix(transform)
                glBindFramebuffer(GL_FRAMEBUFFER, sourceFbo); glViewport(0,0,sourceW,sourceH)
                drawTexture(oesCopy, externalTex, GLES11Ext.GL_TEXTURE_EXTERNAL_OES, transform)
                totalFrames++; lastCaptureAt = System.nanoTime()
                Session.captureHz = totalFrames * 1e9 / (tick - measureStart).coerceAtLeast(1)
                if (tick - lastRead > 180_000_000 && phase == "probe") {
                    lastRead = tick
                    val data = pixels!!; data.clear()
                    glReadPixels(0,0,sourceW,sourceH,GL_RGBA,GL_UNSIGNED_BYTE,data)
                    val result = inspect(data,sourceW,sourceH)
                    sample(result.first, result.second)
                } else if (phase == "probe") sample(false,null)
            }
            haveFrame = true
        }
        val selection=Session.shader
        val renderKey=selection
        val changed=appliedState!=selection
        if (haveFrame && (fresh || changed || (!externalFrames && tick-lastEffect>33_000_000)) && (preview || phase=="shader" || phase=="crt")) {
            try {
                if(rejectedRender!=renderKey) {
                    if(pipeline?.definition?.choice?.id!=selection.id) {
                        val next=ShaderPipeline(context,ShaderCatalog.load(context,selection.id),sourceW,sourceH,processW,processH)
                        pipeline?.release();pipeline=next
                    }
                    pipeline!!.render(sourceTex,((tick-started)/16_666_667).toInt(),selection)
                    rejectedRender=null
                }
            }catch(error:Exception) {
                if(!preview)throw error
                pipeline?.release();pipeline=null;rejectedRender=renderKey
                failure("Shader unavailable: ${error.message}. Choose another shader.")
            }
            appliedState=selection
            lastEffect = tick
            if(externalFrames && fresh) {
                // Full-frame readback is a diagnostic, not part of ordinary native-resolution rendering.
                if(diagnosticMarkers && processedFrames++%30==0) {
                    val out=resultPixels?:ByteBuffer.allocateDirect(processW*processH*4).also{resultPixels=it};out.clear()
                    glReadPixels(0,0,processW,processH,GL_RGBA,GL_UNSIGNED_BYTE,out)
                    var difference=0L
                    for(y in 0..7)for(x in 0..7) {
                        val sx=((x+.5)*sourceW/8).toInt();val sy=((y+.5)*sourceH/8).toInt()
                        val dx=((x+.5)*processW/8).toInt();val dy=((y+.5)*processH/8).toInt()
                        for(c in 0..2)difference+=abs((out.get((dy*processW+dx)*4+c).toInt() and 255)-(frame!!.data.get((sy*sourceW+sx)*4+c).toInt() and 255))
                    }
                    measuredDelta=difference/192.0
                }
                processed(measuredDelta)
            }
        }
        glBindFramebuffer(GL_FRAMEBUFFER,0); glViewport(0,0,outputW,outputH)
        glDisable(GL_SCISSOR_TEST)
        val isBlack = bfiInOutput && (phase == "bfi" || (if (preview) Session.previewBfi && Session.bfi else Session.bfi)) && blackSlot(tick,started,hz,Session.pattern)
        lastBlack=isBlack
        if (phase == "bfi" || phase == "baseline") {
            glClearColor(0f,0f,0f,if (phase == "bfi" && isBlack) 1f else 0f); glClear(GL_COLOR_BUFFER_BIT)
            return
        }
        glClearColor(0f,0f,0f,1f); glClear(GL_COLOR_BUFFER_BIT)
        if (!haveFrame) return
        if (preview) {
            // Matching unfiltered/filtered halves of one animation, with no capture dependency.
            glViewport(0,0,outputW/2,outputH); drawTexture(copy,sourceTex,GL_TEXTURE_2D)
            glViewport(outputW/2,0,outputW-outputW/2,outputH)
            if (!isBlack) drawTexture(copy,pipeline?.resultTexture?:sourceTex,GL_TEXTURE_2D)
        } else if (phase == "probe") {
            drawTexture(copy,sourceTex,GL_TEXTURE_2D)
            glEnable(GL_SCISSOR_TEST)
            glScissor(0,outputH*3/4,outputW/4,outputH/4); glClearColor(1f,0f,1f,1f); glClear(GL_COLOR_BUFFER_BIT)
            glScissor(outputW/4,outputH*3/4,outputW/4,outputH/4); glClearColor(0f,1f,1f,1f); glClear(GL_COLOR_BUFFER_BIT)
            glDisable(GL_SCISSOR_TEST)
        } else if (!isBlack) {
            drawTexture(copy,if(phase=="crt" || Session.crt) pipeline!!.resultTexture else sourceTex,GL_TEXTURE_2D)
            if(phase=="crt" && diagnosticMarkers) {
                glEnable(GL_SCISSOR_TEST)
                for(i in 0..3) {
                    val x=if(i%2==0).06 else .94;val y=if(i<2).08 else .92
                    val w=max(4,outputW/40);val h=max(4,outputH/40)
                    glScissor((outputW*x).toInt()-w/2,(outputH*y).toInt()-h/2,w,h)
                    if(i%2==0)glClearColor(1f,0f,1f,1f) else glClearColor(0f,1f,1f,1f)
                    glClear(GL_COLOR_BUFFER_BIT)
                }
                glDisable(GL_SCISSOR_TEST)
            }
        }
    }
    private fun uploadScene(tick: Long) {
        val c = Canvas(bitmap); c.save(); c.translate(0f,360f); c.scale(1f,-1f)
        c.drawColor(Color.rgb(20,23,38)); paint.color=Color.rgb(66,67,90); paint.strokeWidth=1f
        for(x in 0..640 step 32) c.drawLine(x.toFloat(),0f,x.toFloat(),360f,paint)
        for(y in 0..360 step 32) c.drawLine(0f,y.toFloat(),640f,y.toFloat(),paint)
        val t=(tick-started)/1e9
        paint.color=Color.rgb(255,187,70)
        c.drawCircle((320+240*sin(t*2)).toFloat(),(175+100*abs(sin(t*3))).toFloat(),30f,paint)
        paint.color=Color.WHITE; paint.textSize=24f; c.drawText("Shader · moving source",24f,40f,paint)
        c.restore(); glBindTexture(GL_TEXTURE_2D,sourceTex); GLUtils.texImage2D(GL_TEXTURE_2D,0,bitmap,0)
    }
    private fun fail(message: String) { broken=true; failure(message) }
    fun reportFailure(message: String) { if(!broken)fail(message) }
    private fun texture(type: Int): Int {
        val id=IntArray(1); glGenTextures(1,id,0); glBindTexture(type,id[0])
        glTexParameteri(type,GL_TEXTURE_MIN_FILTER,GL_LINEAR); glTexParameteri(type,GL_TEXTURE_MAG_FILTER,GL_LINEAR)
        glTexParameteri(type,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE); glTexParameteri(type,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE)
        return id[0]
    }
    private fun target(tex: Int,w: Int,h: Int): Int {
        glBindTexture(GL_TEXTURE_2D,tex); glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA,w,h,0,GL_RGBA,GL_UNSIGNED_BYTE,null)
        val ids=IntArray(1); glGenFramebuffers(1,ids,0); glBindFramebuffer(GL_FRAMEBUFFER,ids[0]); glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,tex,0)
        check(glCheckFramebufferStatus(GL_FRAMEBUFFER)==GL_FRAMEBUFFER_COMPLETE) { "Incomplete render target" }; return ids[0]
    }
    private fun drawTexture(p: Int,tex: Int,type: Int,matrix: FloatArray?=null) {
        glUseProgram(p); glActiveTexture(GL_TEXTURE0); glBindTexture(type,tex)
        glUniform1i(glGetUniformLocation(p,"Texture"),0)
        matrix?.let { glUniformMatrix4fv(glGetUniformLocation(p,"Transform"),1,false,it,0) }
        bindAttribute(p,"VertexCoord",0)
        bindAttribute(p,"TexCoord",2)
        glDrawArrays(GL_TRIANGLE_STRIP,0,4)
    }
    private fun bindAttribute(p: Int,name: String,offset: Int) {
        val attr=glGetAttribLocation(p,name)
        if(attr>=0) { vertices.position(offset);glEnableVertexAttribArray(attr);glVertexAttribPointer(attr,2,GL_FLOAT,false,16,vertices) }
    }
    private fun program(vertex: String,fragment: String): Int {
        fun compile(type: Int,code: String): Int {
            val shader=glCreateShader(type); glShaderSource(shader,code); glCompileShader(shader)
            val status=IntArray(1); glGetShaderiv(shader,GL_COMPILE_STATUS,status,0)
            if(status[0]==0) { val message=glGetShaderInfoLog(shader); glDeleteShader(shader); error(message) }
            return shader
        }
        val v=compile(GL_VERTEX_SHADER,vertex); val f=compile(GL_FRAGMENT_SHADER,fragment)
        val p=glCreateProgram(); glAttachShader(p,v); glAttachShader(p,f); glLinkProgram(p); glDeleteShader(v); glDeleteShader(f)
        val result=IntArray(1); glGetProgramiv(p,GL_LINK_STATUS,result,0)
        if(result[0]==0) { val msg=glGetProgramInfoLog(p); glDeleteProgram(p); error(msg) }; return p
    }
    private fun destroyTargets() {
        pipeline?.release();pipeline=null;appliedState=null;rejectedRender=null
        glDeleteTextures(1,intArrayOf(sourceTex),0); glDeleteFramebuffers(1,intArrayOf(sourceFbo),0)
    }
    fun release() {
        submitted.set(null);resultPixels=null
        input?.setOnFrameAvailableListener(null); inputSurface?.release(); input?.release(); input=null
        destroyTargets(); glDeleteTextures(1,intArrayOf(externalTex),0)
        for(p in intArrayOf(copy,oesCopy)) if(p!=0) glDeleteProgram(p)
        if (preview) bitmap.recycle()
    }
    companion object {
        private val IDENTITY=floatArrayOf(1f,0f,0f,0f,0f,1f,0f,0f,0f,0f,1f,0f,0f,0f,0f,1f)
        private const val VERTEX="attribute vec4 VertexCoord; attribute vec2 TexCoord; varying vec2 uv; void main(){gl_Position=VertexCoord;uv=TexCoord;}"
        private const val VERTEX_OES="attribute vec4 VertexCoord; attribute vec2 TexCoord; uniform mat4 Transform; varying vec2 uv; void main(){gl_Position=VertexCoord;uv=(Transform*vec4(TexCoord,0.,1.)).xy;}"
        private const val FRAGMENT="precision highp float; uniform sampler2D Texture; varying vec2 uv; void main(){gl_FragColor=vec4(texture2D(Texture,uv).rgb,1.);}"
        private const val FRAGMENT_OES="#extension GL_OES_EGL_image_external : require\nprecision mediump float; uniform samplerExternalOES Texture; varying vec2 uv; void main(){gl_FragColor=texture2D(Texture,uv);}"
        fun inspect(data: ByteBuffer,w: Int,h: Int): Pair<Boolean,Int?> {
            fun rgb(x: Int,y: Int): IntArray { val p=(y.coerceIn(0,h-1)*w+x.coerceIn(0,w-1))*4; return IntArray(3){ data.get(p+it).toInt() and 255 } }
            var magenta=0; var cyan=0
            for(y in 0 until h step 8) for(x in 0 until w step 8) {
                val c=rgb(x,y)
                if(c[0]>210&&c[1]<40&&c[2]>210) magenta++
                if(c[0]<40&&c[1]>210&&c[2]>210) cyan++
            }
            var counter: Int?=null
            for(flip in listOf(false,true)) {
                fun row(v: Double)=((if(flip) 1-v else v)*h).toInt()
                val header=rgb(w/2,row(.895))
                if(header[0]<220||header[1] !in 85..175||header[2]>40) continue
                val bits=IntArray(32); var valid=true
                for(i in 0..31) {
                    val c=rgb(((i+.5)*w/32).toInt(),row(.95))
                    bits[i]=if(c.all{it>210}) 1 else if(c.all{it<40}) 0 else { valid=false; 0 }
                }
                if(valid&&(0..15).all{bits[it]!=bits[it+16]}) {
                    counter=(0..15).sumOf{bits[it] shl it}; break
                }
            }
            return (magenta>10&&cyan>10) to counter
        }
    }
}
