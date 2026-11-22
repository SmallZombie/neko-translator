package me.avgt.s9e.client.hud

import me.avgt.s9e.client.chat.StyledSegment
import me.avgt.s9e.client.chat.StyledText
import me.avgt.s9e.client.lang.LanguageKeys
import me.avgt.s9e.client.translate.LineGroup
import me.avgt.s9e.common.config.NekoConfig
import me.avgt.s9e.common.translate.TranslationCoordinator
import net.minecraft.network.chat.Component

/**
 * Translation of the text the HUD draws that the player can neither click nor aim at: the
 * title, the subtitle, the action bar message and the names of the boss bars.
 *
 * <p>The title group has its switch in the settings and is translated as soon as it appears.
 * The boss bars have a switch as well, but they only translate on their own while
 * [NekoConfig.autoTranslate] is on; the `translate bossbar` key switches them by hand. One `[T]`
 * behind the text describes the state in both cases.
 *
 * <p>One component is one unit. Text the client language already provides is left alone, exactly
 * like an item name.
 */
class HudTextTranslator(
    private val config: NekoConfig,
    private val coordinator: TranslationCoordinator,
    private val language: () -> String,
) {
    /** One text that is being shown, together with the segments it is built from. */
    private class Unit(val entry: LineGroup, val segments: List<StyledSegment>)

    private val entries = object : LinkedHashMap<String, LineGroup>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LineGroup>): Boolean =
            size > MAX_ENTRIES
    }

    /** The boss bars of the frame that is being drawn; the key switches exactly those. */
    private val visibleBossbars = ArrayList<LineGroup>()

    /** The title, the subtitle and the action bar message. */
    fun title(component: Component): Component {
        val unit = unitOf(component, config.translateTitles) ?: return component
        if (unit.entry.state == LineGroup.State.IDLE) unit.entry.start(coordinator)
        return decorated(component, unit)
    }

    /** The name of one boss bar. */
    fun bossbar(component: Component): Component {
        val unit = unitOf(component, config.translateBossbar) ?: return component
        visibleBossbars.add(unit.entry)
        if (config.autoTranslate && unit.entry.state == LineGroup.State.IDLE) unit.entry.start(coordinator)
        return decorated(component, unit)
    }

    /** Called at the start of every boss bar frame: the set of visible bars starts over. */
    fun beginBossbarFrame() {
        visibleBossbars.clear()
    }

    /** Called by the boss bar key: translates, retries or shows the original of the visible bars. */
    fun toggleBossbar(): Boolean {
        if (!config.enabled || !config.translateBossbar || visibleBossbars.isEmpty()) return false

        for (entry in visibleBossbars) entry.toggle(coordinator)
        return true
    }

    /** The unit of one text, or null when the switch is off, the text is blank or already localized. */
    private fun unitOf(component: Component, enabled: Boolean): Unit? {
        if (!config.enabled || !enabled) return null

        val segments = StyledText.flatten(component)
        val payload = StyledText.payload(segments)
        if (payload.isBlank()) return null

        val language = language()
        // A translation is only pointless when the target is what the client already shows.
        val skipLocalized = language == LanguageKeys.clientLanguage()
        if (skipLocalized && !LanguageKeys.hasForeignText(component)) return null

        val entry = entries.getOrPut(LineGroup.key(language, listOf(payload))) {
            LineGroup(listOf(payload), booleanArrayOf(true))
        }
        return Unit(entry, segments)
    }

    /** The text with its translation and the `[T]` marker behind it. */
    private fun decorated(component: Component, unit: Unit): Component {
        val translated = unit.entry.translation(0)
        val text = if (translated == null) {
            component
        } else {
            // The model did not keep the segment markers; show the plain text, like a chat line.
            StyledText.rebuild(translated, unit.segments) ?: Component.literal(StyledText.stripMarkers(translated))
        }

        return text.copy().append(Component.literal(" ")).append(unit.entry.marker())
    }

    private companion object {
        const val MAX_ENTRIES = 256
    }
}
