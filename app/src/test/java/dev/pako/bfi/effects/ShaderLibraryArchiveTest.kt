package dev.pako.bfi.effects

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test

class ShaderLibraryArchiveTest {
    private val revision="a".repeat(40)
    private val source="#version 450\n#pragma stage vertex\nvoid main(){gl_Position=vec4(0.);}\n#pragma stage fragment\nlayout(location=0) out vec4 FragColor;\nvoid main(){FragColor=vec4(1.); }"
    private fun fixture(files:Map<String,String>,block:(File,File)->Unit) {
        val temp=kotlin.io.path.createTempDirectory("shader-library-test").toFile()
        try {
            val zip=File(temp,"source.zip")
            ZipOutputStream(zip.outputStream()).use{out->files.forEach{(path,value)->out.putNextEntry(ZipEntry("slang-shaders-$revision/$path"));out.write(value.toByteArray());out.closeEntry()}}
            block(zip,File(temp,"library"))
        }finally{temp.deleteRecursively()}
    }
    @Test fun preservesDependenciesNoticesCategoriesAndStableFeaturedIds()=fixture(mapOf(
        "crt/crt-mattias.slangp" to "#reference \"../presets/base.slangp\"",
        "presets/base.slangp" to "shaders=1\nshader0=../include/common.slang",
        "include/common.slang" to source,
        "vhs/test.slangp" to "shaders=1\nshader0=../include/common.slang",
        "LICENSE" to "Original license notice"
    )){zip,root->
        val folder=ShaderLibraryArchive.install(zip,root,revision)
        val choices=ShaderLibraryArchive.choices(folder,revision)
        val mattias=choices.single{it.id=="mattias"}
        assertEquals("crt",mattias.category)
        assertEquals(setOf("crt","vhs","presets"),choices.map{it.category}.toSet())
        val definition=ShaderCatalog.loadChoice(mattias){ShaderLibraryArchive.open(root,it).bufferedReader().use{reader->reader.readText()}}
        assertEquals(source,definition.passes.single().source)
        assertEquals("Original license notice",File(folder,"LICENSE").readText())
        assertEquals(folder,ShaderLibraryArchive.install(zip,root,revision))
        assertThrows(IllegalArgumentException::class.java){ShaderCatalog.resolve(mattias.assetPath,"../../escape.slang")}
    }
    @Test fun failedPresetsAreExcludedWithoutDeletingTheirDependencies()=fixture(mapOf(
        "crt/a.slangp" to "shaders=1\nshader0=../stock.slang",
        "crt/b.slangp" to "shaders=1\nshader0=../stock.slang",
        "stock.slang" to source
    )){zip,root->
        val folder=ShaderLibraryArchive.install(zip,root,revision)
        File(folder,".bfi-compatible").writeText("crt/a.slangp")
        assertEquals(listOf("crt/a"),ShaderLibraryArchive.choices(folder,revision).map{it.label})
        assertEquals(2,ShaderLibraryArchive.choices(folder,revision,false).size)
        assertTrue(File(folder,"crt/b.slangp").isFile)
        assertTrue(File(folder,"stock.slang").isFile)
        File(folder,".bfi-compatible").writeText("")
        assertTrue(ShaderLibraryArchive.choices(folder,revision).isEmpty())
    }
    @Test fun badArchiveNeverReplacesAnExistingSnapshot()=fixture(mapOf("../escape.slangp" to "shaders=1")){zip,root->
        val old=File(root,"b".repeat(40)).apply{mkdirs()};File(old,"kept").writeText("old")
        assertThrows(IllegalArgumentException::class.java){ShaderLibraryArchive.install(zip,root,revision)}
        assertEquals("old",File(old,"kept").readText())
        assertFalse(File(root,"pending-$revision").exists());assertFalse(File(root,revision).exists())
    }
    @Test fun cancellationDiscardsPartialSnapshot()=fixture(mapOf("test.slangp" to "shaders=1")){zip,root->
        assertThrows(java.util.concurrent.CancellationException::class.java){ShaderLibraryArchive.install(zip,root,revision){throw java.util.concurrent.CancellationException()}}
        assertFalse(File(root,revision).exists());assertFalse(File(root,"pending-$revision").exists())
    }
    @Test fun archiveWithoutPresetsIsRejected()=fixture(mapOf("README.md" to "no presets")){zip,root->
        assertThrows(IllegalArgumentException::class.java){ShaderLibraryArchive.install(zip,root,revision)}
    }
    @Test fun rejectsPathsOutsideInstalledLibraryAndMalformedRevision() {
        assertThrows(IllegalArgumentException::class.java){ShaderLibraryArchive.revision("../bad")}
        assertThrows(IllegalArgumentException::class.java){ShaderLibraryArchive.open(File("/tmp"),"library/$revision/../../secret")}
        assertEquals("general",ShaderChoice("id","Test","library/$revision/nearest.slangp").category)
    }
}
