package me.avgt.s9e.client.translate

import me.avgt.s9e.client.lang.LanguageKeys
import me.avgt.s9e.client.util.Feed
import me.avgt.s9e.common.text.NekoText
import me.avgt.s9e.common.translate.TranslationCoordinator
import me.avgt.s9e.common.translate.TranslationResult
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style

/**
 * Translation state of one thing whose lines are always translated, shown and switched back
 * together: an item's tooltip or the text of a sign.
 *
 * <p>The lines share one coloured `[T]` marker that describes the unit (grey = original,
 * yellow = working, green = translation shown, red = failed). Each line is translated on its own
 * request so it keeps its own formatting and shares the translation cache with chat.
 */
class LineGroup(private val payloads: List<String>, private val requestable: BooleanArray) {
    enum class State { IDLE, TRANSLATING, DONE, FAILED }

    private val translations = arrayOfNulls<String>(payloads.size)

    var state: State = State.IDLE
        private set

    var showingOriginal: Boolean = false
        private set

    /** The translation of one line, or null when the original has to be shown. */
    fun translation(index: Int): String? {
        if (state != State.DONE || showingOriginal) return null
        if (!requestable[index]) return null
        return translations[index]?.takeIf { it.isNotBlank() }
    }

    /** Translates, shows the original, or retries after a failure. */
    fun toggle(coordinator: TranslationCoordinator) {
        when (state) {
            State.IDLE -> start(coordinator)
            // While a translation is running a click does nothing on purpose, like the chat marker.
            State.TRANSLATING -> Unit
            State.DONE -> showingOriginal = !showingOriginal
            State.FAILED -> retry(coordinator)
        }
    }

    /** Starts the translation of every requestable line. */
    fun start(coordinator: TranslationCoordinator) {
        val indexes = requestable.indices.filter { requestable[it] }
        translations.fill(null)
        showingOriginal = false

        if (indexes.isEmpty()) {
            state = State.DONE
            return
        }

        state = State.TRANSLATING
        var remaining = indexes.size
        var failure: NekoText? = null

        for (index in indexes) {
            coordinator.requestForChat(payloads[index]).whenComplete { result, _ ->
                Minecraft.getInstance().execute {
                    when (result) {
                        is TranslationResult.Ok -> translations[index] = result.text
                        is TranslationResult.Err -> if (failure == null) failure = result.message
                        null -> if (failure == null) failure = NekoText.of("neko-translator.error.translate_failed")
                    }

                    remaining--
                    if (remaining == 0) {
                        val message = failure
                        state = if (message == null) State.DONE else State.FAILED
                        if (message != null) Feed.error(message)
                    }
                }
            }
        }
    }

    /** The `[T]` marker of this unit. */
    fun marker(): Component = Component.literal(MARKER).withStyle(markerStyle())

    fun markerStyle(): Style = Style.EMPTY.withColor(color())

    private fun retry(coordinator: TranslationCoordinator) {
        for (index in requestable.indices) {
            if (requestable[index]) coordinator.forgetFailure(payloads[index])
        }
        start(coordinator)
    }

    private fun color(): ChatFormatting = when (state) {
        State.TRANSLATING -> ChatFormatting.YELLOW
        State.DONE -> if (showingOriginal) ChatFormatting.GRAY else ChatFormatting.GREEN
        State.FAILED -> ChatFormatting.RED
        State.IDLE -> ChatFormatting.GRAY
    }

    companion object {
        /** The marker that is appended behind a translated unit. */
        const val MARKER = "[T]"

        /** Style of the marker while nothing is known about the unit yet (grey = original). */
        val IDLE_STYLE: Style = Style.EMPTY.withColor(ChatFormatting.GRAY)

        fun idleMarker(): Component = Component.literal(MARKER).withStyle(IDLE_STYLE)

        /** Identity of one unit: its lines, the target language and the loaded language index. */
        fun key(language: String, payloads: List<String>): String = buildString {
            append(LanguageKeys.generation)
            append('\u0000')
            append(language)
            for (payload in payloads) {
                append('\u0000')
                append(payload)
            }
        }
    }
}
