package dev.pako.bfi.effects

import android.content.Context
import java.io.InputStream
import kotlin.math.round

data class ShaderChoice(val id: String, val label: String, val assetPath: String) {
    val category:String get() = when {
        assetPath.startsWith("library/") -> assetPath.split('/',limit=3)[2].let{if('/' in it)it.substringBefore('/') else "general"}
        assetPath.startsWith("imports/") -> "imported"
        assetPath.startsWith("shaders/upstream/slang/") -> assetPath.removePrefix("shaders/upstream/slang/").substringBefore('/')
        else -> "general"
    }
}
data class ShaderParameter(val id: String, val label: String, val defaultValue: Float, val min: Float, val max: Float, val step: Float) {
    val editable get() = min < max
    val toggle get() = min == 0f && max == 1f && step == 1f
    fun sliderValues(): List<Float> {
        if (!editable) return listOf(min)
        val count = kotlin.math.ceil((max.toDouble() - min) / step).toInt()
        return ((0..count).map { (min + it * step).coerceAtMost(max) } + defaultValue).distinct().sorted()
    }
    fun normalize(value: Float): Float {
        if (!value.isFinite() || value == defaultValue) return defaultValue
        if (value >= max) return max
        if (value <= min) return min
        if (!editable) return min
        val bounded = value.coerceIn(min, max)
        return (min.toDouble() + round((bounded.toDouble() - min) / step) * step).toFloat().coerceIn(min, max)
    }
}
data class ShaderPass(val path: String, val source: String)
data class ShaderLookup(val name: String, val path: String, val linear: Boolean, val repeat: Boolean, val mipmap: Boolean)
data class ShaderDefinition(val choice: ShaderChoice, val passes: List<ShaderPass>, val parameters: List<ShaderParameter>, val settings: Map<String, String>, val lookups: List<ShaderLookup>)
data class ShaderState(val id: String, val values: Map<String, Float>)

object ShaderCatalog {
    val choices = listOf(
        ShaderChoice("lottes", "CRT Lottes", "shaders/upstream/slang/crt/crt-lottes.slangp"),
        ShaderChoice("newpixie", "Newpixie CRT", "shaders/upstream/slang/crt/newpixie-crt.slangp"),
        ShaderChoice("zfast", "zfast CRT Composite", "shaders/upstream/slang/crt/zfast-crt-composite.slangp"),
        ShaderChoice("geom", "CRT Geom", "shaders/upstream/slang/crt/crt-geom.slangp"),
        ShaderChoice("none", "No CRT shader", "passthrough.slang")
    )
    private val number = "[-+]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][-+]?[0-9]+)?"
    private val pragma = Regex("^\\s*#pragma\\s+parameter\\s+(\\w+)\\s+\"([^\"]*)\"\\s+($number)\\s+($number)\\s+($number)(?:\\s+($number))?\\s*(?://.*)?$")
    private val include = Regex("^\\s*#include\\s+\"([^\"]+)\"\\s*(?://.*)?$")

    fun choices(context:Context):List<ShaderChoice> = (choices + ShaderLibrary.choices(context) + ShaderImports.choices(context)).distinctBy{it.id}
    fun open(context:Context,path:String):InputStream = when {
        path.startsWith("imports/") -> ShaderImports.open(context,path)
        path.startsWith("library/") -> ShaderLibrary.open(context,path)
        else -> context.assets.open(path)
    }
    fun load(context: Context, id: String): ShaderDefinition {
        val choice=choices(context).firstOrNull{it.id==id}?:error("Selected shader is unavailable. Download the library or choose another shader.")
        var textBudget=8*1024*1024
        return loadChoice(choice){path->
            val bytes=open(context,path).use{it.readNBytesBounded(2*1024*1024)}
            textBudget-=bytes.size;require(textBudget>=0){"Shader sources expand beyond 8 MiB"}
            bytes.toString(Charsets.UTF_8)
        }
    }
    internal fun load(id: String, read: (String) -> String): ShaderDefinition =
        loadChoice(choices.firstOrNull { it.id == id } ?: choices.first(),read)

    internal fun loadChoice(choice:ShaderChoice,read:(String)->String):ShaderDefinition {
        val settings = if (choice.id in setOf("none")) linkedMapOf("shaders" to "1", "shader0" to choice.assetPath, "scale_type0" to "viewport", "scale0" to "1.0")
            else preset(choice.assetPath,read,emptySet())
        val count = requireNotNull(settings["shaders"]?.toIntOrNull()) { "Missing shader pass count" }
        require(count in 1..16) { "Invalid shader pass count" }
        val passes = (0 until count).map { index ->
            val path = if (choice.id in setOf("none")) choice.assetPath else resolve(choice.assetPath, requireNotNull(settings["shader$index"]) { "Missing shader pass $index" })
            ShaderPass(path, expand(path, read, emptySet()))
        }
        val parameters = linkedMapOf<String, ShaderParameter>()
        passes.forEach { pass -> parseParameters(pass.source).forEach { parameter -> parameters.putIfAbsent(parameter.id, parameter) } }
        // These two upstream Rainbow declarations have no shader code behind them.
        if (choice.id == "hyllian_ntsc_rainbow") parameters.keys.removeAll(setOf("SHARPNESS_HACK", "CRT_ANTI_RINGING"))
        val defaults = parameters.values.map { parameter ->
            val override = settings[parameter.id]?.toFloatOrNull()?.takeIf { it.isFinite() }
            if (override == null) parameter else parameter.copy(defaultValue = override.coerceIn(parameter.min, parameter.max))
        }
        val lookups = settings["textures"].orEmpty().split(';').filter { it.isNotBlank() }.map { name ->
            ShaderLookup(name, resolve(choice.assetPath, requireNotNull(settings[name]) { "Missing texture $name" }),
                settings["${name}_linear"] == "true" || settings["${name}_filter_linear"] == "true",
                settings["${name}_wrap_mode"] == "repeat", settings["${name}_mipmap"] == "true")
        }
        require(defaults.size<=512){"Too many shader parameters"}
        require(defaults.sumOf{if(it.editable)(it.max.toDouble()-it.min)/it.step else 0.0}<=200_000){"Shader parameter ranges are too large"}
        return ShaderDefinition(choice, passes, defaults, settings.toMap(), lookups)
    }

    private fun preset(path:String,read:(String)->String,ancestors:Set<String>):LinkedHashMap<String,String> {
        require(ancestors.size<16 && path !in ancestors){"Recursive or overly deep preset reference"}
        val source=read(path)
        val result=linkedMapOf<String,String>()
        source.lineSequence().forEach{line->
            val reference=Regex("^\\s*#reference\\s+\"([^\"]+)\"\\s*(?://.*)?$").matchEntire(line)
            if(reference!=null) {
                val base=resolve(path,reference.groupValues[1]);val inherited=preset(base,read,ancestors+path)
                // Referenced paths remain relative to their own preset, including LUTs.
                val pathKeys=inherited.keys.filter{it.matches(Regex("shader[0-9]+"))} + inherited["textures"].orEmpty().split(';').filter{it.isNotBlank()}
                pathKeys.forEach{key->inherited[key]?.let{inherited[key]=relative(path,resolve(base,it))}}
                result.putAll(inherited)
            }
        }
        result.putAll(parseSettings(source));return result
    }
    private fun relative(from:String,to:String):String {
        val base=from.substringBeforeLast('/').split('/');val target=to.split('/')
        val common=base.zip(target).takeWhile{it.first==it.second}.size
        return (List(base.size-common){".."}+target.drop(common)).joinToString("/")
    }

    internal fun parseSettings(source: String): LinkedHashMap<String, String> {
        val settings = linkedMapOf<String, String>()
        source.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith('#') }.forEach { line ->
            val pair = line.split('=', limit = 2)
            require(pair.size == 2) { "Invalid shader preset line: $line" }
            val value = pair[1].trim()
            settings[pair[0].trim()] = if (value.startsWith('"')) {
                require(value.length >= 2 && value.indexOf('"', 1) >= 1) { "Unclosed preset value" }
                value.substring(1, value.indexOf('"', 1))
            } else value.substringBefore('#').trim()
        }
        return settings
    }

    internal fun parseParameters(source: String): List<ShaderParameter> = source.lineSequence().mapNotNull { line ->
        if (!line.trimStart().startsWith("#pragma parameter")) return@mapNotNull null
        val match = requireNotNull(pragma.matchEntire(line)) { "Invalid shader parameter: $line" }
        val values = (3..5).map { match.groupValues[it].toFloat() }
        val (default, min, max) = values
        val step = match.groupValues[6].takeIf { it.isNotEmpty() }?.toFloat() ?: (max - min) / 10f
        require(values.all { it.isFinite() } && (min == max || (max.toDouble()-min)/step <= 100_000) && min <= max && default in min..max && step.isFinite() && (step > 0f || min == max)) { "Invalid shader parameter bounds: $line" }
        ShaderParameter(match.groupValues[1], match.groupValues[2], default, min, max, step)
    }.toList()

    private fun expand(path: String, read: (String) -> String, ancestors: Set<String>): String {
        require(path !in ancestors) { "Recursive shader include: $path" }
        require(ancestors.size<32){"Shader include depth exceeds 32"}
        return read(path).lineSequence().joinToString("\n") { line ->
            val match = include.matchEntire(line)
            if (match == null) line else expand(resolve(path, match.groupValues[1]), read, ancestors + path)
        }
    }

    internal fun resolve(from: String, relative: String): String {
        require(relative.isNotBlank() && !relative.startsWith('/') && !relative.contains('\\') && !relative.contains(':')) { "Invalid shader asset path" }
        val parts = mutableListOf<String>()
        (from.substringBeforeLast('/', "") + "/" + relative).split('/').forEach { part ->
            when (part) {
                "", "." -> Unit
                ".." -> { require(parts.isNotEmpty()) { "Shader asset path escapes assets" }; parts.removeAt(parts.lastIndex) }
                else -> parts.add(part)
            }
        }
        val path = parts.joinToString("/")
        val importedRoot=from.takeIf{it.startsWith("imports/") || it.startsWith("library/")}?.split('/')?.take(2)?.joinToString("/")
        require(if(importedRoot!=null)path.startsWith("$importedRoot/") else path.startsWith("shaders/upstream/glsl/") || path.startsWith("shaders/upstream/slang/")) { "Shader asset path escapes bundled shader catalog" }
        return path
    }
}
