package dev.pako.bfi.ui

import dev.pako.bfi.effects.ShaderChoice
import org.junit.Assert.*
import org.junit.Test

class ShaderCategoriesTest {
    private val choices=listOf(
        ShaderChoice("crt","CRT","shaders/upstream/slang/crt/test.slangp"),
        ShaderChoice("vhs","VHS","shaders/upstream/slang/vhs/test.slangp"),
        ShaderChoice("vhs2","VHS 2","shaders/upstream/slang/vhs/other.slangp")
    )
    @Test fun noFavouritesStartsWithAllAndKeepsBundledLast(){
        assertEquals(listOf("all","crt","vhs","bundled"),shaderCategories(choices,emptySet()))
    }
    @Test fun availableFavouriteAddsFirstCategoryWithoutDuplicatingSourceCategories(){
        assertEquals(listOf("favourites","all","crt","vhs","bundled"),shaderCategories(choices,setOf("vhs")))
    }
    @Test fun unavailableFavouritesDoNotLeaveAnEmptyCategory(){
        assertFalse("favourites" in shaderCategories(choices,setOf("removed")))
        assertEquals(listOf("all","bundled"),shaderCategories(emptyList(),setOf("removed")))
    }
}
