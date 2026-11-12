package dev.pako.bfi.effects

object ShaderSource {
    fun compile(source: String, vertex: Boolean, slang: Boolean): String {
        require(!Regex("(?m)^[ \\t]*#include\\b").containsMatchIn(source)) {
            "Expand shader includes before adapting the source"
        }
        var body = if (slang) stage(source, vertex) else source
        body = body.replace(Regex("(?m)^[ \\t]*#(?:version|pragma)\\b[^\\r\\n]*"), "")
        if (slang) {
            body = scalarSwizzles(body)
            val layouts=Regex("\\blayout\\s*\\(([^)]*)\\)").findAll(withoutComments(body)).toList()
            for(match in layouts.asReversed()) {
                require(match.groupValues[1].split(',').all {
                    it.trim().matches(Regex("(?:push_constant|std140|(?:set|binding|location)\\s*=\\s*\\d+)"))
                }) { "Unsupported shader layout: ${match.value}" }
                body=body.replaceRange(match.range,"")
            }
            body = body.replace(Regex("\\buniform\\s+(\\w+)\\s*\\{([^{}]*)\\}\\s*(\\w+)?\\s*;")) { match ->
                val (type, fields, instance) = match.destructured
                if (instance.isEmpty()) {
                    withoutComments(fields).split(';').filter { it.isNotBlank() }
                        .joinToString("\n") { "uniform ${it.trim()};" }
                } else {
                    "struct $type {$fields};\nuniform $type $instance;"
                }
            }
            require(!Regex("\\buniform\\s+\\w+\\s*\\{").containsMatchIn(body)) {
                "Unsupported shader uniform block (nested blocks and instance arrays are not supported)"
            }
            // ES has no implicit uint-to-float conversion for the catalog's FrameCount mod calls.
            body = body.replace(Regex("\\bmod\\(\\s*((?:params|global)\\.FrameCount)(\\s*\\+\\s*\\d+)?\\s*,\\s*(\\d+(?:\\.\\d*)?)\\s*\\)(?:\\s*\\*\\s*(\\d+)(?![\\w.]))?")) {
                val (field, increment, divisor, multiplier) = it.destructured
                val count = field + if (increment.isEmpty()) "" else "${increment}u"
                "mod(float($count), float($divisor))" + if (multiplier.isEmpty()) "" else " * float($multiplier)"
            }
            body = body.replace(Regex("(\\bmod\\(\\s*\\w+(?:\\.\\w+)?\\s*,\\s*\\d+\\.\\d*\\s*\\)\\s*\\+\\s*)((?:global|params)\\.FrameCount)\\b")) {
                "${it.groupValues[1]}float(${it.groupValues[2]})"
            }
            body = body.replace(Regex("\\b(params\\.FrameCount)(?=\\s*/\\s*\\d+\\.)")) { "float(${it.value})" }
            body = body.replace(Regex("\\b(params\\.FrameDirection)(?=\\s*[<>]=?\\s*-?\\d+\\.)")) { "float(${it.value})" }
            body = body.replace(Regex("\\bTIME\\s*/\\s*100\\b(?!\\.)"), "TIME / 100.0")
            body = body.replace(Regex("\\bparams\\.scaleInLinearGamma\\s*==\\s*0\\b(?!\\.)"), "params.scaleInLinearGamma == 0.0")
            body = body.replace(Regex("\\?\\s*200\\.0\\s*:\\s*1000\\b(?!\\.)"), "? 200.0 : 1000.0")
            body = body.replace(Regex("\\bvec3\\s+(\\w+)\\[(\\d+)\\]\\[(\\d+)\\]\\s*=\\s*\\{((?:\\s*\\{[^{}]*\\}\\s*,?\\s*)+)\\}\\s*;")) {
                val (name, rows, columns, values) = it.destructured
                val constructors = Regex("\\{([^{}]*)\\}").findAll(values).map { row ->
                    "vec3[$columns](${row.groupValues[1].trim().trimEnd(',')})"
                }.toList()
                require(constructors.size == rows.toInt()) { "Unsupported shader mask array: ${it.value}" }
                "vec3 $name[$rows][$columns] = vec3[$rows][$columns](${constructors.joinToString(", ")});"
            }
            body = body.replace(Regex("\\b1\\s*-\\s*fp\\.y\\b"), "1.0 - fp.y")
            // These upstream Jzazbz helpers use float-only math with desktop integer promotion.
            body = body.replace(Regex("\\bvec3\\s+(?:XYZ_to_Jzazbz|Jzazbz_to_XYZ)\\s*\\([^{}]*\\)\\s*\\{[^{}]*\\}")) { function ->
                function.value.replace(Regex("(?<![\\w.])(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?[fFuU]?(?![\\w.])")) { number ->
                    if(number.value.all{it.isDigit()})number.value+".0" else number.value
                }
            }
            body = initializeGlobals(body)
        }
        val defines = if (slang) "" else "#define ${if (vertex) "VERTEX" else "FRAGMENT"}\n#define PARAMETER_UNIFORM\n"
        return "#version 310 es\nprecision highp float;\nprecision highp int;\n$defines$body"
    }

    private fun scalarSwizzles(source: String): String {
        val scalars = mutableSetOf<String>()
        val blocks = Regex("\\buniform\\s+\\w+\\s*\\{([^{}]*)\\}\\s*(\\w+)\\s*;")
        for (block in blocks.findAll(source)) {
            for (field in Regex("\\bfloat\\s+([^;{}]+);").findAll(withoutComments(block.groupValues[1]))) {
                for (name in field.groupValues[1].split(',').map { it.trim() }) {
                    scalars += "${block.groupValues[2]}.$name"
                    if (Regex("(?m)^#define[ \\t]+${Regex.escape(name)}\\b").containsMatchIn(source)) scalars += name
                }
            }
        }
        for (field in Regex("\\bconst\\s+float\\s+(\\w+)\\s*=").findAll(source)) scalars += field.groupValues[1]
        if (scalars.isEmpty()) return source
        val names = scalars.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }
        return source.replace(Regex("\\b($names)\\.(x{1,4})\\b")) {
            val (scalar, components) = it.destructured
            if (components.length == 1) scalar else "vec${components.length}($scalar)"
        }
    }

    private fun stage(source: String, vertex: Boolean): String {
        val stages = Regex("(?m)^[ \\t]*#pragma[ \\t]+stage[ \\t]+([^\\r\\n]+)").findAll(withoutComments(source)).toList()
        for (stage in stages) {
            require(stage.groupValues[1].trim() in listOf("vertex", "fragment")) {
                "Unsupported Slang stage: ${stage.groupValues[1]}"
            }
        }
        require(stages.size == 2 && stages.map { it.groupValues[1].trim() }.toSet().size == 2) {
            "Slang source requires one vertex and one fragment stage"
        }
        val index = stages.indexOfFirst { it.groupValues[1].trim() == if (vertex) "vertex" else "fragment" }
        val end = stages.getOrNull(index + 1)?.range?.first ?: source.length
        return source.substring(0, stages.first().range.first) + source.substring(stages[index].range.last + 1, end)
    }

    private fun initializeGlobals(source: String): String {
        // shortcut: catalog globals use single-line declarations, extend only when a new upstream shader needs it.
        val declaration = Regex("([ \\t]*)([biu]?vec[234]|mat[234]|float|int|uint|bool)\\s+(\\w+)\\s*=\\s*(.+);[ \\t]*")
        val assignments = mutableListOf<String>()
        val maskedLines = withoutComments(source).lines()
        var depth = 0
        val body = source.lines().mapIndexed { index, line ->
            val masked = maskedLines[index]
            val match = if (depth == 0) declaration.matchEntire(masked) else null
            if (!masked.trimStart().startsWith('#')) depth += masked.count { it == '{' } - masked.count { it == '}' }
            if (match == null) line else {
                val (indent, type, name) = match.destructured
                val value = line.substring(match.groups[4]!!.range).trimEnd()
                assignments += "$name = $value;"
                "$indent$type $name;${line.substring(masked.lastIndexOf(';') + 1)}"
            }
        }.joinToString("\n")
        if (assignments.isEmpty()) return body
        val main = Regex("\\bvoid\\s+main\\s*\\(\\s*(?:void\\s*)?\\)\\s*\\{").find(withoutComments(body))
        require(main != null) { "Shader with runtime global initializers has no main function" }
        return body.substring(0, main.range.last + 1) + "\n" + assignments.joinToString("\n") + "\n" + body.substring(main.range.last + 1)
    }

    private fun withoutComments(source: String): String =
        source.replace(Regex("/\\*[\\s\\S]*?\\*/|//[^\\r\\n]*")) { match ->
            match.value.map { if (it == '\n' || it == '\r') it else ' ' }.joinToString("")
        }
}
