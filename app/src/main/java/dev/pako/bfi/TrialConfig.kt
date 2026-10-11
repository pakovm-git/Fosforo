package dev.pako.bfi

object TrialConfig {
    const val MIN_SECONDS = 1
    const val MAX_SECONDS = 600
    const val DEFAULT_SECONDS = 30

    fun parseSeconds(text: String): Int? = text.trim().toIntOrNull()?.takeIf(::isValid)
    fun isValid(seconds: Int) = seconds in MIN_SECONDS..MAX_SECONDS
}
