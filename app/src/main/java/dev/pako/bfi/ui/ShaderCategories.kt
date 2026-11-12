package dev.pako.bfi.ui

import dev.pako.bfi.effects.ShaderChoice

internal fun shaderCategories(choices:List<ShaderChoice>,favourites:Set<String>):List<String> = buildList {
    if(choices.any{it.id in favourites})add("favourites")
    add("all")
    addAll(choices.map{it.category}.distinct().filterNot{it in setOf("all","favourites","bundled")}.sorted())
    add("bundled")
}
