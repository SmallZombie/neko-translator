package me.avgt.s9e.common.translate

/** Outcome of a single translation attempt. */
sealed class TranslationResult {
    data class Ok(val text: String) : TranslationResult()

    /** A failure message that is safe to show to the local player. */
    data class Err(val message: String) : TranslationResult()
}
