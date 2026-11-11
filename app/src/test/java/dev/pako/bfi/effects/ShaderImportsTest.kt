package dev.pako.bfi.effects

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test

class ShaderImportsTest {
    private val shader="""
        #version 450
        #pragma parameter GAIN "Gain" 1 .1 2 .1
        #pragma stage vertex
        void main(){gl_Position=vec4(0.);}
        #pragma stage fragment
        layout(location=0)out vec4 FragColor;
        void main(){FragColor=vec4(1.);}
    """.trimIndent()
    private fun zip(files:Map<String,String>):File = File.createTempFile("shader-test-",".zip").apply {
        ZipOutputStream(outputStream()).use{zip->files.forEach{(path,text)->zip.putNextEntry(ZipEntry(path));zip.write(text.toByteArray());zip.closeEntry()}}
    }
    private fun directory(block:(File)->Unit) {val dir=kotlin.io.path.createTempDirectory("shader-import-test").toFile();try{return block(dir)}finally{dir.deleteRecursively()}}
    @Test fun importsSelectedPresetAndOnlyItsDependencyClosure()=directory{dir->
        ShaderPackage(zip(mapOf("crt/test.slangp" to "shaders=1\nshader0=../common/source.slang", "common/source.slang" to shader,"unrelated/other.txt" to "unused"))).use{archive->
            assertEquals(listOf("crt/test.slangp"),archive.presets)
            val choice=archive.extract("0".repeat(24),archive.presets.single(),dir)
            assertEquals("import_"+"0".repeat(24),choice.id)
            assertTrue(File(dir,"common/source.slang").isFile);assertFalse(File(dir,"unrelated/other.txt").exists())
        }
    }
    @Test fun referencePathsStayRelativeToTheirOwningPreset()=directory{dir->
        ShaderPackage(zip(mapOf("variants/test.slangp" to "#reference \"../base/base.slangp\"\nGAIN=1.5", "base/base.slangp" to "shaders=1\nshader0=../common/source.slang", "common/source.slang" to shader))).use{archive->
            val choice=archive.extract("1".repeat(24),"variants/test.slangp",dir)
            val definition=ShaderCatalog.loadChoice(choice){path->File(dir,path.split('/',limit=3)[2]).readText()}
            assertEquals(1.5f,definition.parameters.single().defaultValue,0f)
            assertTrue(definition.passes.single().path.endsWith("/common/source.slang"))
        }
    }
    @Test fun importsSelfContainedSlang()=directory{dir->
        val file=File.createTempFile("single-shader-",".slang").apply{writeText(shader)}
        ShaderPackage(file,"My CRT.slang").use{archive->val choice=archive.extract("2".repeat(24),archive.presets.single(),dir);assertEquals("My CRT (imported)",choice.label);assertEquals(shader,File(dir,"source.slang").readText())}
    }
    @Test fun rejectsTraversalAndMissingDependencies()=directory{dir->
        val bad=zip(mapOf("../escape.slangp" to "shaders=1"))
        try{assertThrows(IllegalArgumentException::class.java){ShaderPackage(bad)}}finally{bad.delete()}
        ShaderPackage(zip(mapOf("test.slangp" to "shaders=1\nshader0=missing.slang"))).use{archive->
            val error=assertThrows(IllegalStateException::class.java){archive.extract("3".repeat(24),archive.presets.single(),dir)}
            assertTrue(error.message.orEmpty().contains("Missing shader dependency"))
        }
        listOf("/absolute","a/../../escape","a\\b","a//b","C:/x","a/./b").forEach{path->assertThrows(IllegalArgumentException::class.java){ShaderPackage.entryPath(path)}}
    }
    @Test fun rejectsRecursiveReferencesAndUnsupportedFormats()=directory{dir->
        ShaderPackage(zip(mapOf("test.slangp" to "#reference \"test.slangp\""))).use{archive->
            assertThrows(IllegalArgumentException::class.java){archive.extract("4".repeat(24),archive.presets.single(),dir)}
        }
        ShaderPackage(zip(mapOf("test.slangp" to "shaders=1\nshader0=source.slang","source.slang" to shader.replace("#pragma stage fragment","#pragma stage fragment\n#pragma format R32G32B32A32_SFLOAT")))).use{archive->
            assertThrows(IllegalStateException::class.java){archive.extract("5".repeat(24),archive.presets.single(),dir)}
        }
    }
}
