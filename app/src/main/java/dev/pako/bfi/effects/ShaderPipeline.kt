package dev.pako.bfi.effects

import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.GLES30.*
import android.opengl.GLUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/** Runs only the bundled presets: real pass outputs, previous-frame feedback, LUTs and framebuffer formats. */
class ShaderPipeline(private val context:Context,val definition:ShaderDefinition,
                     private val originalW:Int,private val originalH:Int,private val viewportW:Int,private val viewportH:Int) {
    private data class Image(val texture:Int,val fbo:Int,val w:Int,val h:Int)
    private data class Uniform(val name:String,val type:Int,val location:Int)
    private data class Pass(val program:Int,val uniforms:List<Uniform>,var targets:List<Image>)
    private val passes=mutableListOf<Pass>()
    private val lookups=linkedMapOf<String,Image>()
    private val aliases=definition.passes.indices.associateBy { definition.settings["alias$it"].orEmpty() }.filterKeys{it.isNotEmpty()}
    private val vertices=ByteBuffer.allocateDirect(64).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(floatArrayOf(-1f,-1f,0f,0f, 1f,-1f,1f,0f, -1f,1f,0f,1f, 1f,1f,1f,1f));position(0)
    }
    private var parity=0
    private var lastTarget:Image?=null
    val resultTexture get()=checkNotNull(lastTarget).texture
    val resultWidth get()=passes.last().targets[0].w
    val resultHeight get()=passes.last().targets[0].h
    private val borderSupported=(glGetString(GL_EXTENSIONS).orEmpty().contains("GL_EXT_texture_border_clamp") || glGetString(GL_EXTENSIONS).orEmpty().contains("GL_OES_texture_border_clamp"))
    init {
        try {
            var w=originalW;var h=originalH;var memory=0L
            definition.passes.forEach { pass ->
                val p=link(ShaderSource.compile(pass.source,true,pass.path.endsWith(".slang",true)),ShaderSource.compile(pass.source,false,pass.path.endsWith(".slang",true)))
                try {passes+=Pass(p,uniforms(p),emptyList())}
                catch(error:Exception){glDeleteProgram(p);throw error}
            }
            val feedback=shaderFeedbackTargets(passes.flatMap{pass->pass.uniforms.filter{it.type==GL_SAMPLER_2D}.map{it.name.substringAfterLast('.')}},aliases,definition.settings["feedback_pass"]?.toIntOrNull()?:0,passes.size)
            passes.forEachIndexed { index,pass ->
                val size=shaderPassSize(definition.settings,index,index==definition.passes.lastIndex,w,h,viewportW,viewportH)
                w=size.first;h=size.second
                val format=shaderTargetFormat(definition.settings,index,definition.passes[index].source)
                // Only sampled history requires two targets; current-only passes can reuse one.
                val count=if(index in feedback)2 else 1
                memory+=w.toLong()*h*(if(format==GL_RGBA16F)8 else 4)*count
                require(memory<=128L*1024*1024){"Shader pass targets exceed 128 MiB"}
                repeat(count){pass.targets=pass.targets+target(w,h,format)}
            }
            definition.lookups.forEach { lookup ->
                val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
                ShaderCatalog.open(context,lookup.path).use{BitmapFactory.decodeStream(it,null,bounds)}
                require(bounds.outWidth in 1..8192 && bounds.outHeight in 1..8192){"Invalid shader texture: ${lookup.name}"}
                memory+=bounds.outWidth.toLong()*bounds.outHeight*4
                require(memory<=128L*1024*1024){"Shader textures and targets exceed 128 MiB"}
                val bitmap=ShaderCatalog.open(context,lookup.path).use { BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply {inScaled=false;inPremultiplied=false}) }
                checkNotNull(bitmap){"Could not load ${lookup.name}"}
                val ids=IntArray(1);glGenTextures(1,ids,0)
                try {
                    glBindTexture(GL_TEXTURE_2D,ids[0]);GLUtils.texImage2D(GL_TEXTURE_2D,0,bitmap,0)
                    lookups[lookup.name]=Image(ids[0],0,bitmap.width,bitmap.height)
                }catch(error:Exception){glDeleteTextures(1,ids,0);throw error}
                finally {bitmap.recycle()}
            }
            checkGl("Shader setup")
            android.util.Log.i("ShaderGPU","Loaded ${definition.choice.id}: ${passes.size} passes; targets=${memory/1024/1024} MiB; GLES=${glGetString(GL_VERSION)} border=$borderSupported")
        }catch(error:Exception){release();throw IllegalStateException("${definition.choice.label}: ${error.message}",error)}
    }
    fun render(sourceTexture:Int,frameCount:Int,state:ShaderState):Int {
        val original=Image(sourceTexture,0,originalW,originalH)
        val parameters=definition.parameters.associate { it.id to it.normalize(state.values[it.id]?:it.defaultValue) }
        val current=passes.map{it.targets[parity%it.targets.size]}
        val previous=passes.map{it.targets[(1-parity)%it.targets.size]}
        passes.forEachIndexed { index,pass ->
            val source=if(index==0)original else current[index-1]
            val output=current[index]
            glBindFramebuffer(GL_FRAMEBUFFER,output.fbo);glViewport(0,0,output.w,output.h)
            glDisable(GL_BLEND);glDisable(GL_SCISSOR_TEST)
            glUseProgram(pass.program)
            var unit=0
            fun image(name:String):Image = when {
                name=="Source" || name=="Texture" -> source
                name=="Original" || name=="orig" -> original
                name=="Feedback" -> previous[definition.settings["feedback_pass"]?.toIntOrNull()?:0]
                name.startsWith("PassFeedback") -> previous[name.removePrefix("PassFeedback").toInt()]
                name.startsWith("PassOutput") -> current[name.removePrefix("PassOutput").toInt().also{check(it<index){"Forward shader dependency"}}]
                name.endsWith("Feedback") && aliases.containsKey(name.removeSuffix("Feedback")) -> previous[aliases.getValue(name.removeSuffix("Feedback"))]
                aliases.containsKey(name) -> current[aliases.getValue(name).also{check(it<index){"Forward alias dependency"}}]
                lookups.containsKey(name) -> lookups.getValue(name)
                else -> error("Unsupported shader input $name")
            }
            pass.uniforms.forEach { uniform ->
                val key=uniform.name.substringAfterLast('.')
                val location=uniform.location
                when {
                    uniform.type==GL_SAMPLER_2D -> {
                        val texture=image(key)
                        check(texture.texture!=output.texture){"Cannot sample current output"}
                        val lookup=definition.lookups.firstOrNull{it.name==key}
                        val sampling=shaderSamplingPass(key,index,aliases,definition.settings["feedback_pass"]?.toIntOrNull()?:0)
                        val linear=lookup?.linear?: (definition.settings["filter_linear$sampling"]=="true")
                        val mipmap=lookup?.mipmap?: (definition.settings["mipmap_input$sampling"]=="true")
                        val wrap=if(lookup!=null)definition.settings["${lookup.name}_wrap_mode"].orEmpty() else definition.settings["wrap_mode$sampling"].orEmpty()
                        glActiveTexture(GL_TEXTURE0+unit);glBindTexture(GL_TEXTURE_2D,texture.texture)
                        val wrapValue=when(wrap){"repeat"->GL_REPEAT;"mirrored_repeat"->GL_MIRRORED_REPEAT;"clamp_to_border"->{check(borderSupported){"Border clamp extension unavailable"};0x812D};else->GL_CLAMP_TO_EDGE}
                        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,wrapValue);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,wrapValue)
                        if(wrapValue==0x812D)glTexParameterfv(GL_TEXTURE_2D,0x1004,floatArrayOf(0f,0f,0f,0f),0)
                        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,if(linear)GL_LINEAR else GL_NEAREST)
                        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,if(mipmap){if(linear)GL_LINEAR_MIPMAP_LINEAR else GL_NEAREST_MIPMAP_NEAREST}else if(linear)GL_LINEAR else GL_NEAREST)
                        if(mipmap)glGenerateMipmap(GL_TEXTURE_2D)
                        glUniform1i(location,unit++)
                    }
                    uniform.type==GL_FLOAT_MAT4 && key in setOf("MVP","MVPMatrix") -> glUniformMatrix4fv(location,1,false,IDENTITY,0)
                    key=="FrameCount" -> { val modulo=definition.settings["frame_count_mod$index"]?.toIntOrNull()?:0;val count=if(modulo>0)frameCount%modulo else frameCount; if(uniform.type==GL_UNSIGNED_INT)glUniform1ui(location,count)else glUniform1i(location,count) }
                    key=="FrameDirection" -> glUniform1i(location,1)
                    key.endsWith("Size") || key=="InputSize" -> {
                        val size=when(key) {
                            "OutputSize"-> output.w to output.h
                            "FinalViewportSize","ViewportSize"->viewportW to viewportH
                            "OriginalSize"->originalW to originalH
                            "SourceSize","InputSize","TextureSize"->source.w to source.h
                            else->image(key.removeSuffix("Size")).let{it.w to it.h}
                        }
                        if(uniform.type==GL_FLOAT_VEC4)glUniform4f(location,size.first.toFloat(),size.second.toFloat(),1f/size.first,1f/size.second)
                        else glUniform2f(location,size.first.toFloat(),size.second.toFloat())
                    }
                    uniform.type==GL_FLOAT -> glUniform1f(location,parameters[key]?:definition.settings[key]?.toFloatOrNull()?:error("Unsupported shader uniform ${uniform.name}"))
                    else -> error("Unsupported uniform ${uniform.name}")
                }
            }
            bind(pass.program,"VertexCoord",0);bind(pass.program,"Position",0);bind(pass.program,"TexCoord",2)
            val color=glGetAttribLocation(pass.program,"COLOR")
            if(color>=0){glDisableVertexAttribArray(color);glVertexAttrib4f(color,1f,1f,1f,1f)}
            glDrawArrays(GL_TRIANGLE_STRIP,0,4)
        }
        checkGl("${definition.choice.label} rendering")
        lastTarget=current.last();parity=1-parity
        return resultTexture
    }
    private fun bind(program:Int,name:String,offset:Int) {
        val attr=glGetAttribLocation(program,name)
        if(attr>=0){vertices.position(offset);glEnableVertexAttribArray(attr);glVertexAttribPointer(attr,2,GL_FLOAT,false,16,vertices)}
    }
    private fun target(w:Int,h:Int,format:Int):Image {
        val max=IntArray(1);glGetIntegerv(GL_MAX_TEXTURE_SIZE,max,0)
        require(w in 1..max[0] && h in 1..max[0]){"Shader target too large: ${w}x$h"}
        val texture=IntArray(1);val fbo=IntArray(1)
        glGenTextures(1,texture,0);glGenFramebuffers(1,fbo,0)
        try {
            glBindTexture(GL_TEXTURE_2D,texture[0]);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR)
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE)
            glTexImage2D(GL_TEXTURE_2D,0,format,w,h,0,GL_RGBA,if(format==GL_RGBA16F)GL_HALF_FLOAT else GL_UNSIGNED_BYTE,null)
            glBindFramebuffer(GL_FRAMEBUFFER,fbo[0]);glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,texture[0],0)
            check(glCheckFramebufferStatus(GL_FRAMEBUFFER)==GL_FRAMEBUFFER_COMPLETE){"Framebuffer format $format unavailable"}
            glDisable(GL_SCISSOR_TEST);glClearColor(0f,0f,0f,0f);glClear(GL_COLOR_BUFFER_BIT)
            return Image(texture[0],fbo[0],w,h)
        }catch(error:Exception){glDeleteTextures(1,texture,0);glDeleteFramebuffers(1,fbo,0);throw error}
    }
    private fun uniforms(program:Int):List<Uniform> {
        val count=IntArray(1);glGetProgramiv(program,GL_ACTIVE_UNIFORMS,count,0)
        return (0 until count[0]).mapNotNull { index ->
            val size=IntArray(1);val type=IntArray(1)
            val name=glGetActiveUniform(program,index,size,0,type,0)
            check(size[0]==1){"Uniform arrays are not supported: $name"}
            val location=glGetUniformLocation(program,name)
            if(location<0)null else Uniform(name,type[0],location)
        }
    }
    private fun link(vertex:String,fragment:String):Int {
        fun compile(type:Int,source:String):Int {
            val shader=glCreateShader(type);glShaderSource(shader,source);glCompileShader(shader)
            val status=IntArray(1);glGetShaderiv(shader,GL_COMPILE_STATUS,status,0)
            if(status[0]==0){val message=glGetShaderInfoLog(shader);glDeleteShader(shader);error(message)}
            return shader
        }
        val vertexId=compile(GL_VERTEX_SHADER,vertex)
        var fragmentId=0;var program=0
        try {
            fragmentId=compile(GL_FRAGMENT_SHADER,fragment);program=glCreateProgram();glAttachShader(program,vertexId);glAttachShader(program,fragmentId);glLinkProgram(program)
            val status=IntArray(1);glGetProgramiv(program,GL_LINK_STATUS,status,0)
            check(status[0]!=0){glGetProgramInfoLog(program)}
            return program
        }catch(error:Exception){if(program!=0)glDeleteProgram(program);throw error}
        finally {glDeleteShader(vertexId);if(fragmentId!=0)glDeleteShader(fragmentId)}
    }
    private fun destroy(image:Image){glDeleteTextures(1,intArrayOf(image.texture),0);if(image.fbo!=0)glDeleteFramebuffers(1,intArrayOf(image.fbo),0)}
    fun release(){passes.forEach{pass->glDeleteProgram(pass.program);pass.targets.forEach(::destroy)};passes.clear();lookups.values.forEach(::destroy);lookups.clear();lastTarget=null}
    private fun checkGl(operation:String){val error=glGetError();check(error==GL_NO_ERROR){"$operation: GL error 0x${error.toString(16)}"}}
    companion object {
        private val IDENTITY=floatArrayOf(1f,0f,0f,0f,0f,1f,0f,0f,0f,0f,1f,0f,0f,0f,0f,1f)
    }
}

internal fun shaderFeedbackTargets(names:List<String>,aliases:Map<String,Int>,feedback:Int,count:Int):Set<Int> = names.mapNotNull { name ->
    val index=when {
        name=="Feedback" -> feedback
        name.startsWith("PassFeedback") -> name.removePrefix("PassFeedback").toInt()
        name.endsWith("Feedback") -> aliases[name.removeSuffix("Feedback")]
        else -> null
    }
    index?.also{require(it in 0 until count){"Invalid shader feedback pass $it"}}
}.toSet()

internal fun shaderPassSize(settings:Map<String,String>,index:Int,last:Boolean,sourceW:Int,sourceH:Int,viewportW:Int,viewportH:Int):Pair<Int,Int> {
    fun axis(axis:String,source:Int,viewport:Int):Int {
        val type=settings["scale_type_${axis}$index"]?:settings["scale_type$index"]?:if(last)"viewport" else "source"
        val scale=(settings["scale_${axis}$index"]?:settings["scale$index"]?:"1").toDouble()
        require(scale.isFinite() && scale>0){"Invalid shader scale"}
        val value=when(type){"source"->source*scale;"viewport"->viewport*scale;"absolute"->scale;else->error("Unsupported scale type $type")}
        require(value in 1.0..16384.0){"Invalid shader dimensions"}
        return value.roundToInt()
    }
    return axis("x",sourceW,viewportW) to axis("y",sourceH,viewportH)
}

// RetroArch assigns output sampling from its next pass, and feedback sampling from its own pass.
internal fun shaderSamplingPass(name:String,current:Int,aliases:Map<String,Int>,feedback:Int):Int = when {
    name=="Feedback" -> feedback
    name.startsWith("PassFeedback") -> name.removePrefix("PassFeedback").toInt()
    name.startsWith("PassOutput") -> name.removePrefix("PassOutput").toInt()+1
    name.endsWith("Feedback") && aliases.containsKey(name.removeSuffix("Feedback")) -> aliases.getValue(name.removeSuffix("Feedback"))
    aliases.containsKey(name) -> aliases.getValue(name)+1
    else -> current
}

internal fun shaderTargetFormat(settings:Map<String,String>,index:Int,source:String):Int {
    require(settings["rgb10_framebuffer$index"]!="true"){"RGB10 shader targets are not supported"}
    val floating=settings["float_framebuffer$index"]=="true"
    val srgb=settings["srgb_framebuffer$index"]=="true"
    require(!(floating && srgb)){"Conflicting float and sRGB shader target flags"}
    val format=Regex("(?m)^\\s*#pragma\\s+format\\s+(\\w+)").find(source)?.groupValues?.get(1)
    val declared=when(format){null,"R8G8B8A8_UNORM"->GL_RGBA8;"R8G8B8A8_SRGB"->GL_SRGB8_ALPHA8;"R16G16B16A16_SFLOAT"->GL_RGBA16F;else->error("Unsupported shader target format: $format")}
    // Enabled preset flags override the shader's declared format, as in RetroArch.
    return when{floating->GL_RGBA16F;srgb->GL_SRGB8_ALPHA8;else->declared}
}
