package me.avgt.s9e.client.chat

import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style

/** A run of text that shares one [Style]. */
data class StyledSegment(val text: String, val style: Style)

/**
 * Helpers that keep the formatting of a chat message while it is translated.
 *
 * <p>Colour, bold, italic and strikethrough live in the component tree, not in the plain
 * string the model sees. To keep them, the message is split into styled segments, the
 * segments are sent to the model with small index markers, and the answer is reassembled
 * with the original styles. If the model does not keep the markers intact (or the answer
 * shape is unexpected) the caller falls back to an unstyled literal.
 */
object StyledText {
    private const val OPEN = '\u27E6'
    private const val CLOSE = '\u27E7'
    private val MARKER = Regex("\\u27E6(\\d+)\\u27E7")

    /** Vanilla player chat lines start with `<Name>`; that part must never be translated. */
    private val PLAYER_PREFIX = Regex("^<[^<>\\s]{1,40}> ?")

    /** The message split into a fixed prefix (`<Name> `) and the translatable body. */
    data class Parts(val prefix: List<StyledSegment>, val body: List<StyledSegment>, val raw: String)

    fun split(component: Component): Parts {
        val segments = flatten(component)
        val raw = join(segments)
        val match = PLAYER_PREFIX.find(raw)

        if (match == null) {
            return Parts(emptyList(), segments, raw)
        }

        val cut = match.value.length
        return Parts(slice(segments, 0, cut), slice(segments, cut, raw.length), raw)
    }

    fun flatten(component: Component): List<StyledSegment> =
        component.toFlatList().map { StyledSegment(it.string, it.style) }

    fun join(segments: List<StyledSegment>): String = buildString {
        for (segment in segments) append(segment.text)
    }

    /** The payload for the model: plain text when there is only one style, marked segments otherwise. */
    fun payload(segments: List<StyledSegment>): String {
        if (segments.size <= 1) return join(segments)

        return buildString {
            segments.forEachIndexed { index, segment ->
                append(OPEN).append(index).append(CLOSE)
                append(segment.text)
            }
        }
    }

    /** True for payloads that are neither blank nor a single character; those are never sent. */
    fun isTranslatable(payload: String): Boolean = payload.length > 1 && payload.isNotBlank()

    fun toComponent(segments: List<StyledSegment>): Component {
        val result = Component.empty()
        for (segment in segments) {
            result.append(Component.literal(segment.text).withStyle(segment.style))
        }
        return result
    }

    /** Removes leftover markers, used when the model did not keep them intact. */
    fun stripMarkers(text: String): String = MARKER.replace(text, "")

    /**
     * Rebuilds the translated body with the original styles, or returns {@code null} when the
     * marked text cannot be mapped back (caller then shows the plain translation).
     */
    fun rebuild(text: String, segments: List<StyledSegment>): Component? {
        if (segments.isEmpty()) return Component.literal(text)
        if (segments.size == 1) return Component.literal(text).withStyle(segments[0].style)

        val matches = MARKER.findAll(text).toList()
        if (matches.size != segments.size) return null
        if (text.substring(0, matches.first().range.first).isNotBlank()) return null

        val used = BooleanArray(segments.size)
        val result = Component.empty()

        for ((position, match) in matches.withIndex()) {
            val index = match.groupValues[1].toIntOrNull() ?: return null
            if (index !in segments.indices || used[index]) return null
            used[index] = true

            val start = match.range.last + 1
            val end = if (position + 1 < matches.size) matches[position + 1].range.first else text.length
            result.append(Component.literal(text.substring(start, end)).withStyle(segments[index].style))
        }

        return result
    }

    private fun slice(segments: List<StyledSegment>, from: Int, to: Int): List<StyledSegment> {
        val result = ArrayList<StyledSegment>()
        var position = 0

        for (segment in segments) {
            val start = position
            val end = position + segment.text.length
            position = end

            if (end <= from || start >= to) continue

            val localStart = maxOf(from, start) - start
            val localEnd = minOf(to, end) - start
            if (localEnd > localStart) {
                result.add(StyledSegment(segment.text.substring(localStart, localEnd), segment.style))
            }
        }

        return result
    }
}
