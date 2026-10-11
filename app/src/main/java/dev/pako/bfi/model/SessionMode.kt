package dev.pako.bfi.model

sealed interface SessionMode {
    data object Continuous : SessionMode
    data class Timed(val seconds: Int) : SessionMode { init { require(seconds in 1..600) } }
}
