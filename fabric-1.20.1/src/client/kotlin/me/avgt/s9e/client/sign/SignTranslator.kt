package me.avgt.s9e.client.sign

import java.util.IdentityHashMap
import me.avgt.s9e.client.chat.StyledSegment
import me.avgt.s9e.client.chat.StyledText
import me.avgt.s9e.client.lang.LanguageKeys
import me.avgt.s9e.client.translate.LineGroup
import me.avgt.s9e.client.util.Feed
import me.avgt.s9e.common.config.NekoConfig
import me.avgt.s9e.common.text.NekoText
import me.avgt.s9e.common.translate.TranslationCoordinator
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSequence
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.entity.SignBlockEntity
import net.minecraft.world.level.block.entity.SignText
import net.minecraft.world.phys.BlockHitResult

/**
 * Translation of the text of the sign the player aims at.
 *
 * <p>One side of a sign is one unit: its lines are translated together, shown together and
 * switched back together, and one coloured `[T]` behind the last line describes that unit
 * (grey = original, yellow = working, green = translation shown, red = failed). The marker has
 * no tooltip - the crosshair can never rest on it. Sign text is a layout of up to four short
 * lines, so a line whose translation is wider than the sign is cut at the sign's own limit;
 * the `copy sign` key exists to read the full original in chat instead.
 *
 * <p>With automatic translation on a sign is translated once the crosshair rested on it for
 * [DWELL] ticks, so signs that are merely swept over are left alone. The same text always
 * shares the same state, which means two identical signs are translated once and look alike.
 */
class SignTranslator(
    private val config: NekoConfig,
    private val coordinator: TranslationCoordinator,
    private val language: () -> String,
) {
    /** One sign line together with the styled segments it is built from. */
    private class Line(val component: Component, val segments: List<StyledSegment>, val needsTranslation: Boolean) {
        val payload: String = StyledText.payload(segments)
        val requestable: Boolean = needsTranslation && StyledText.isTranslatable(payload)
    }

    private class Parsed(val lines: List<Line>, val payloads: List<String>, val key: String) {
        val hasWork: Boolean = lines.any { it.requestable }
    }

    /** The parsed lines of one [SignText]; the renderer asks for them on every frame. */
    private class Cached(val filtered: Boolean, val language: String, val generation: Int, val parsed: Parsed)

    private val entries = object : LinkedHashMap<String, LineGroup>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LineGroup>): Boolean =
            size > MAX_ENTRIES
    }

    private val parsed = IdentityHashMap<SignText, Cached>()

    /** The sign the crosshair rested on last tick, and how many ticks it has been resting there. */
    private var aimed: SignText? = null
    private var dwell = 0

    /**
     * Called by the renderer for every side of every visible sign: shows the translation and
     * hangs the `[T]` behind the last line.
     */
    fun display(
        signText: SignText,
        filtered: Boolean,
        original: Array<FormattedCharSequence>,
        prepare: (Component) -> FormattedCharSequence,
    ): Array<FormattedCharSequence> {
        if (!config.enabled) return original

        val parsed = parse(signText, filtered)
        if (!parsed.hasWork) return original

        val entry = entries[parsed.key]
        val result = original.copyOf()

        for ((index, line) in parsed.lines.withIndex()) {
            val shown = translated(entry, line, index) ?: continue
            result[index] = prepare(shown)
        }

        // The marker goes behind the last line that carries something. At least one line is
        // requestable here, so this always finds one.
        val last = parsed.lines.indexOfLast { it.requestable || it.component.string.isNotBlank() }
        result[last] = FormattedCharSequence.composite(
            result[last],
            FormattedCharSequence.forward(" " + LineGroup.MARKER, entry?.markerStyle() ?: LineGroup.IDLE_STYLE),
        )
        return result
    }

    /** Follows the crosshair and starts the automatic translation of a sign that is looked at. */
    fun tick() {
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player
        if (!config.enabled || player == null || minecraft.screen != null) {
            clearAim()
            return
        }

        val sign = aimedSign(minecraft) ?: run {
            clearAim()
            return
        }
        val text = textOf(sign, player)

        if (text !== aimed) {
            aimed = text
            dwell = 0
        }
        dwell++

        if (!config.autoTranslate || dwell < DWELL) return

        val parsed = parse(text, minecraft.isTextFilteringEnabled())
        if (!parsed.hasWork) return

        val entry = entryOf(parsed)
        if (entry.state == LineGroup.State.IDLE) entry.start(coordinator)
    }

    /** Handles the `translate sign` key: translate, show the original, or retry. */
    fun toggleAimed(): Boolean {
        if (!config.enabled) return false

        val minecraft = Minecraft.getInstance()
        val player = minecraft.player ?: return false
        if (minecraft.screen != null) return false

        val sign = aimedSign(minecraft) ?: return false
        val parsed = parse(textOf(sign, player), minecraft.isTextFilteringEnabled())
        if (!parsed.hasWork) return false

        entryOf(parsed).toggle(coordinator)
        return true
    }

    /** Handles the `copy sign` key: puts the original text of the aimed sign on the clipboard. */
    fun copyAimed(): Boolean {
        if (!config.enabled) return false

        val minecraft = Minecraft.getInstance()
        val player = minecraft.player ?: return false
        if (minecraft.screen != null) return false

        val sign = aimedSign(minecraft) ?: return false
        val content = textOf(sign, player)
            .getMessages(minecraft.isTextFilteringEnabled())
            .map { it.string }
            .filter { it.isNotBlank() }
            .joinToString(" ")

        if (content.isEmpty()) return false

        minecraft.keyboardHandler.setClipboard(content)
        Feed.info(NekoText.of("neko-translator.sign.copied"))
        return true
    }

    /** The side of the sign the player is looking at. */
    private fun textOf(sign: SignBlockEntity, player: Player): SignText =
        if (sign.isFacingFrontText(player)) sign.frontText else sign.backText

    private fun clearAim() {
        aimed = null
        dwell = 0
    }

    private fun aimedSign(minecraft: Minecraft): SignBlockEntity? {
        val pos = (minecraft.hitResult as? BlockHitResult)?.blockPos ?: return null
        return minecraft.level?.getBlockEntity(pos) as? SignBlockEntity
    }

    private fun entryOf(parsed: Parsed): LineGroup = entries.getOrPut(parsed.key) {
        LineGroup(parsed.payloads, BooleanArray(parsed.lines.size) { parsed.lines[it].requestable })
    }

    private fun parse(signText: SignText, filtered: Boolean): Parsed {
        val language = language()
        val generation = LanguageKeys.generation

        parsed[signText]?.let {
            if (it.filtered == filtered && it.language == language && it.generation == generation) return it.parsed
        }

        // A translation is only pointless when the target is what the client already shows.
        val skipLocalized = language == LanguageKeys.clientLanguage()
        val lines = signText.getMessages(filtered).map { component ->
            Line(component, StyledText.flatten(component), !skipLocalized || LanguageKeys.hasForeignText(component))
        }
        val payloads = lines.map { it.payload }
        val result = Parsed(lines, payloads, LineGroup.key(language, payloads))

        if (parsed.size >= MAX_PARSED) parsed.clear()
        parsed[signText] = Cached(filtered, language, generation, result)
        return result
    }

    /** The translated component of one line, or {@code null} when the original has to be shown. */
    private fun translated(entry: LineGroup?, line: Line, index: Int): Component? {
        val text = entry?.translation(index) ?: return null

        // The model did not keep the segment markers; show the plain text, like a chat line.
        return StyledText.rebuild(text, line.segments) ?: Component.literal(StyledText.stripMarkers(text))
    }

    private companion object {
        /** How many ticks the crosshair has to rest on a sign before it is translated. */
        const val DWELL = 4
        const val MAX_ENTRIES = 256
        const val MAX_PARSED = 512
    }
}
