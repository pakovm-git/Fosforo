package dev.pako.bfi.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.opengl.GLES20.*
import android.opengl.GLSurfaceView
import android.os.*
import android.view.WindowManager
import android.widget.*
import dev.pako.bfi.*
import dev.pako.bfi.model.SessionMode
import dev.pako.bfi.state.Reason
import dev.pako.bfi.state.StartRequest
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** A real capture source. The same overlay used over games is applied for fifteen seconds. */
class TestEffectsActivity:Activity() {
    private val runtime get()=(application as BfiApplication).runtime
    private lateinit var source:GLSurfaceView
    private lateinit var action:Button
    private var ownSession=-1L
    private var pendingCapture:StartRequest?=null
    private var unsubscribe:(()->Unit)?=null
    override fun onCreate(state:Bundle?) {
        super.onCreate(state)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(16,16,16,16)}
        ScreenActivity.configureInsets(root,window)
        val title=TextView(this).apply{text=getString(R.string.test_effects);textSize=22f;setTextColor(getColor(R.color.bfi_text_primary))}
        root.addView(title)
        source=GLSurfaceView(this).apply {
            setEGLContextClientVersion(2);setRenderer(SourceRenderer(false,false));contentDescription=getString(R.string.test_animation)
        }
        root.addView(source,LinearLayout.LayoutParams(-1,0,1f))
        val help=TextView(this).apply{text=getString(R.string.test_help);setTextColor(getColor(R.color.bfi_text_secondary))}
        root.addView(help)
        action=Button(android.view.ContextThemeWrapper(this,R.style.PrimaryAction),null,0)
        root.addView(action,LinearLayout.LayoutParams(-1,-2))
        val back=Button(android.view.ContextThemeWrapper(this,R.style.Action),null,0).apply{text=getString(R.string.back);setOnClickListener{finish()}}
        root.addView(back,LinearLayout.LayoutParams(-1,-2))
        action.setOnClickListener {if(ownSession==runtime.state.session && runtime.state.userEnabled)runtime.stop() else begin()}
        setContentView(root)
    }
    private fun begin() {
        if(runtime.state.userEnabled)return
        val problem=runtime.readiness()
        val request=runtime.prepareStart(SessionMode.Timed(15))
        if(problem==Reason.CAPTURE){pendingCapture=request;startActivityForResult(getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent(),202);return}
        if(problem!=null){Toast.makeText(this,UiText.reason(this,problem),Toast.LENGTH_LONG).show();return}
        startTest(request,null)
    }
    private fun startTest(request:StartRequest,consent:Intent?) {
        if(!runtime.accepts(request))return
        runtime.testing=true;runtime.controlsVisibility(false)
        runtime.start(request,consent)
        if(runtime.state.userEnabled)ownSession=runtime.state.session else runtime.testing=false
    }
    @Deprecated("Native Activity result callback")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==202){val request=pendingCapture;pendingCapture=null;if(resultCode==RESULT_OK && data!=null && request!=null)startTest(request,data)}
    }
    override fun onResume(){super.onResume();source.onResume();unsubscribe=runtime.subscribe {
        action.setText(if(runtime.state.userEnabled && ownSession==runtime.state.session)R.string.stop_effects else R.string.start_test)
    }}
    override fun onPause(){
        if(ownSession==runtime.state.session && runtime.state.userEnabled)runtime.stop()
        runtime.testing=false;unsubscribe?.invoke();unsubscribe=null;source.onPause();super.onPause()
    }
}
private class SourceRenderer(private val probe:Boolean,private val detail:Boolean): GLSurfaceView.Renderer {
    private var program=0
    private val vertices=ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder()).asFloatBuffer().apply{put(floatArrayOf(-1f,-1f,1f,-1f,-1f,1f,1f,1f));position(0)}
    override fun onSurfaceCreated(gl: GL10?,config: EGLConfig?) {
        fun compile(type:Int,code:String):Int {
            val id=glCreateShader(type);glShaderSource(id,code);glCompileShader(id)
            val ok=IntArray(1);glGetShaderiv(id,GL_COMPILE_STATUS,ok,0);check(ok[0]!=0){glGetShaderInfoLog(id)};return id
        }
        val v=compile(GL_VERTEX_SHADER,"attribute vec2 pos; varying vec2 uv; void main(){uv=(pos+1.)*.5;gl_Position=vec4(pos,0.,1.);}")
        val f=compile(GL_FRAGMENT_SHADER,"""
            precision highp float;
            varying vec2 uv;
            uniform float count;
            uniform float time;
            uniform float detail;
            void main(){
                vec3 color=vec3(.078,.09,.15);
                vec2 ball=vec2(.5+.35*sin(time*2.),.5+.2*cos(time*3.));
                if(length(uv-ball)<.045)color=vec3(1.,.73,.27);
                if(detail>0.5 && uv.y>.3 && uv.y<.5)color=vec3(mod(floor(gl_FragCoord.x),2.));
                if(detail>0.5 && uv.y>.55 && uv.y<.75)color=vec3(mod(floor(gl_FragCoord.y),2.));
                gl_FragColor=vec4(color,1.);
            }
        """.trimIndent())
        program=glCreateProgram();glAttachShader(program,v);glAttachShader(program,f);glLinkProgram(program)
        val ok=IntArray(1);glGetProgramiv(program,GL_LINK_STATUS,ok,0);check(ok[0]!=0){glGetProgramInfoLog(program)}
        glDeleteShader(v);glDeleteShader(f)
    }
    override fun onSurfaceChanged(gl: GL10?,w:Int,h:Int){glViewport(0,0,w,h)}
    override fun onDrawFrame(gl: GL10?){
        if(probe) {
            val colors=arrayOf(floatArrayOf(.15f,.35f,.65f),floatArrayOf(.7f,.2f,.4f),floatArrayOf(.3f,.75f,.2f),floatArrayOf(.85f,.7f,.25f),floatArrayOf(.4f,.3f,.8f),floatArrayOf(.6f,.6f,.6f))
            val c=colors[((SystemClock.elapsedRealtime()/400)%colors.size).toInt()]
            glClearColor(c[0],c[1],c[2],1f);glClear(GL_COLOR_BUFFER_BIT);return
        }
        glUseProgram(program)
        glUniform1f(glGetUniformLocation(program,"detail"),if(detail)1f else 0f)
        glUniform1f(glGetUniformLocation(program,"time"),(SystemClock.elapsedRealtime()%60000)/1000f)
        glUniform1f(glGetUniformLocation(program,"count"),((SystemClock.elapsedRealtime()/16)%65536).toFloat())
        val p=glGetAttribLocation(program,"pos");glEnableVertexAttribArray(p);glVertexAttribPointer(p,2,GL_FLOAT,false,0,vertices)
        glDrawArrays(GL_TRIANGLE_STRIP,0,4)
    }
}
