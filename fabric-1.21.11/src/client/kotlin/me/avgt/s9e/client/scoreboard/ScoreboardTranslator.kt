package me.avgt.s9e.client.scoreboard

import me.avgt.s9e.client.chat.StyledSegment
import me.avgt.s9e.client.chat.StyledText
import me.avgt.s9e.client.lang.LanguageKeys
import me.avgt.s9e.client.translate.LineGroup
import me.avgt.s9e.common.config.NekoConfig
import me.avgt.s9e.common.translate.TranslationCoordinator
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.numbers.StyledFormat
import net.minecraft.world.scores.Objective
import net.minecraft.world.scores.PlayerScoreEntry
import net.minecraft.world.scores.PlayerTeam

/**
 * Translation of the sidebar scoreboard: its title and every line.
 *
 * <p>The panel is laid out from its own text - the width is measured before anything is drawn -
 * so a translated line cannot be swapped in afterwards. While the switch is on the whole sidebar
 * is therefore drawn here instead of by vanilla, with the same layout and colours.
 *
 * <p>One scoreboard is one unit: the title and the lines are requested on their own, and the
 * single `[T]` behind the title describes all of them (grey = original, yellow = working,
 * green = translation shown, red = failed). The switch is the only master switch, [NekoConfig.autoTranslate]
 * has no effect on it.
 */
class ScoreboardTranslator(
    private val config: NekoConfig,
    private val coordinator: TranslationCoordinator,
    private val language: () -> String,
) {
    /** One text of the sidebar, together with the segments it is built from. */
    private class Text(
        val component: Component,
        val segments: List<StyledSegment>,
        val payload: String,
        needsTranslation: Boolean,
    ) {
        val requestable: Boolean = needsTranslation && StyledText.isTranslatable(payload)
    }

    /** One line of the sidebar: the name on the left and the score on the right. */
    private class Row(val name: Text, val score: Component)

    private class Parsed(val title: Text, val rows: List<Row>, language: String) {
        val payloads: List<String> = listOf(title.payload) + rows.map { it.name.payload }
        val requestable: BooleanArray = BooleanArray(payloads.size) {
            if (it == 0) title.requestable else rows[it - 1].name.requestable
        }
        val key: String = LineGroup.key(language, payloads)
        val hasWork: Boolean = requestable.any { it }
    }

    private val groups = object : LinkedHashMap<String, LineGroup>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LineGroup>): Boolean =
            size > MAX_GROUPS
    }

    /**
     * Draws the sidebar of [objective] with its translation and returns true; false leaves the
     * sidebar to vanilla (the switch is off or nothing on it can be translated).
     */
    fun display(graphics: GuiGraphics, objective: Objective): Boolean {
        if (!config.enabled || !config.translateScoreboard) return false

        val parsed = parse(objective)
        if (!parsed.hasWork) return false

        val group = groups.getOrPut(parsed.key) { LineGroup(parsed.payloads, parsed.requestable) }
        if (group.state == LineGroup.State.IDLE) group.start(coordinator)

        draw(graphics, parsed, group)
        return true
    }

    /** The texts of the sidebar as the game would show them, split into what is sent. */
    private fun parse(objective: Objective): Parsed {
        val language = language()
        // A translation is only pointless when the target is what the client already shows.
        val skipLocalized = language == LanguageKeys.clientLanguage()
        val scoreboard = objective.scoreboard
        val format = objective.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT)

        val title = text(objective.displayName, skipLocalized)
        val rows = scoreboard.listPlayerScores(objective)
            .filter { !it.isHidden() }
            .sortedWith(DISPLAY_ORDER)
            .take(MAX_ROWS)
            .map { entry ->
                val team = scoreboard.getPlayersTeam(entry.owner())
                Row(
                    text(PlayerTeam.formatNameForTeam(team, entry.ownerName()), skipLocalized),
                    entry.formatValue(format),
                )
            }

        return Parsed(title, rows, language)
    }

    private fun text(component: Component, skipLocalized: Boolean): Text {
        val segments = StyledText.flatten(component)
        val payload = StyledText.payload(segments)
        return Text(component, segments, payload, !skipLocalized || LanguageKeys.hasForeignText(component))
    }

    /** Vanilla layout with translated text; the marker widens the title and the panel with it. */
    private fun draw(graphics: GuiGraphics, parsed: Parsed, group: LineGroup) {
        val minecraft = Minecraft.getInstance()
        val font = minecraft.font
        val rows = parsed.rows

        val title = show(parsed.title, group, 0).copy()
            .append(Component.literal(" "))
            .append(group.marker())
        val titleWidth = font.width(title)
        val spacerWidth = font.width(": ")

        val names = ArrayList<Component>(rows.size)
        val scoreWidths = IntArray(rows.size)
        var width = titleWidth

        for ((index, row) in rows.withIndex()) {
            val name = show(row.name, group, index + 1)
            val scoreWidth = font.width(row.score)
            names.add(name)
            scoreWidths[index] = scoreWidth

            val lineWidth = font.width(name) + if (scoreWidth > 0) spacerWidth + scoreWidth else 0
            if (lineWidth > width) width = lineWidth
        }

        val count = rows.size
        val height = count * LINE_HEIGHT
        val bottom = graphics.guiHeight() / 2 + height / 3
        val left = graphics.guiWidth() - width - 3
        val right = graphics.guiWidth() - 3 + 2
        val headerY = bottom - height
        val background = minecraft.options.getBackgroundColor(0.3F)
        val headerBackground = minecraft.options.getBackgroundColor(0.4F)

        graphics.fill(left - 2, headerY - LINE_HEIGHT - 1, right, headerY - 1, headerBackground)
        graphics.fill(left - 2, headerY - 1, right, bottom, background)
        graphics.drawString(font, title, left + width / 2 - titleWidth / 2, headerY - LINE_HEIGHT, -1, false)

        for (index in 0 until count) {
            val y = bottom - (count - index) * LINE_HEIGHT
            graphics.drawString(font, names[index], left, y, -1, false)
            graphics.drawString(font, rows[index].score, right - scoreWidths[index], y, -1, false)
        }
    }

    /** The text of one line as it is shown: its translation while there is one, else the original. */
    private fun show(text: Text, group: LineGroup, index: Int): Component {
        val translated = group.translation(index) ?: return text.component

        // The model did not keep the segment markers; show the plain text, like a chat line.
        return StyledText.rebuild(translated, text.segments)
            ?: Component.literal(StyledText.stripMarkers(translated))
    }

    private companion object {
        /** Vanilla order of the sidebar: the highest score first, ties by owner name. */
        val DISPLAY_ORDER: Comparator<PlayerScoreEntry> =
            compareByDescending<PlayerScoreEntry> { it.value() }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.owner() }

        /** Vanilla shows at most this many lines. */
        const val MAX_ROWS = 15

        /** Height of one line in the sidebar. */
        const val LINE_HEIGHT = 9

        const val MAX_GROUPS = 256
    }
}
