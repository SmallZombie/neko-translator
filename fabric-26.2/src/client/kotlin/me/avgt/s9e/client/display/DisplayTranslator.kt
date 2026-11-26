package me.avgt.s9e.client.display

import java.util.WeakHashMap
import kotlin.math.abs
import me.avgt.s9e.client.chat.StyledSegment
import me.avgt.s9e.client.chat.StyledText
import me.avgt.s9e.client.lang.LanguageKeys
import me.avgt.s9e.client.translate.LineGroup
import me.avgt.s9e.common.config.NekoConfig
import me.avgt.s9e.common.translate.TranslationCoordinator
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.Display

/**
 * Translation of the text of display entities (holograms).
 *
 * <p>The trigger is the render pass itself. The game calls the text display renderer only for
 * the displays it is about to draw - they passed the entity's own distance check and their
 * chunk section is visible - so that call is the "should render" the entity already has. A
 * display is translated the first time it is drawn close enough to be read, which needs no
 * scan of the loaded entities and no tick hook.
 *
 * <p>One display is one unit: its whole text is sent as one payload, so the model keeps the
 * sentence together instead of translating line by line. The translation only replaces the
 * layout that is drawn - the entity's own text is never touched, nothing is written back.
 *
 * <p>A text display can be scaled, so the readable distance follows its own glyph height: the
 * renderer draws one line as 10 pixels at the 0.025 scale of the text pass, multiplied by the
 * display's own Y scale. A hologram scaled to four times the normal size is translated from
 * four times as far away.
 *
 * <p>The feature is off by default: it spends requests without a click.
 */
class DisplayTranslator(
    private val config: NekoConfig,
    private val coordinator: TranslationCoordinator,
    private val language: () -> String,
) {
    /** Parsed state of one display, keyed by the entity so the renderer finds it in O(1). */
    private class Entry {
        var source: Display.TextDisplay.TextRenderState? = null
        var lineWidth: Int = DEFAULT_LINE_WIDTH
        var segments: List<StyledSegment> = emptyList()
        var group: LineGroup? = null
        var info: Display.TextDisplay.CachedInfo? = null
        var infoText: String? = null
    }

    private val entries = WeakHashMap<Display.TextDisplay, Entry>()

    private val groups = object : LinkedHashMap<String, LineGroup>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LineGroup>): Boolean =
            size > MAX_GROUPS
    }

    /**
     * The layout of the translated text, or the vanilla layout while nothing is translated.
     * Called by the renderer for every text display it is about to draw.
     */
    fun info(
        entity: Display.TextDisplay,
        splitter: Display.TextDisplay.LineSplitter,
    ): Display.TextDisplay.CachedInfo {
        if (!config.enabled || !config.translateDisplays) return entity.cacheDisplay(splitter)

        val entry = entryOf(entity) ?: return entity.cacheDisplay(splitter)
        val text = entry.group?.translation(0) ?: return entity.cacheDisplay(splitter)

        val cached = entry.info
        if (cached != null && entry.infoText == text) return cached

        val info = layout(entry.segments, text, entry.lineWidth)
        entry.info = info
        entry.infoText = text
        return info
    }

    /**
     * The entry of [entity], parsed and started the first time the display is drawn close
     * enough to be read. A display that is still too far away is not parsed at all and is
     * checked again on the next frame.
     */
    private fun entryOf(entity: Display.TextDisplay): Entry? {
        val source = entity.textRenderState() ?: return null

        val cached = entries[entity]
        if (cached != null && cached.source === source) return cached
        if (!reachable(entity)) return null

        val entry = cached ?: Entry().also { entries[entity] = it }
        entry.source = source
        entry.lineWidth = source.lineWidth()
        entry.segments = StyledText.flatten(source.text())
        entry.info = null
        entry.infoText = null
        entry.group = null

        val payload = StyledText.payload(entry.segments)
        if (!StyledText.isTranslatable(payload)) return entry

        val language = language()
        // A translation is only pointless when the target is what the client already shows.
        if (language == LanguageKeys.clientLanguage() && !LanguageKeys.hasForeignText(source.text())) return entry

        val group = groups.getOrPut(LineGroup.key(language, listOf(payload))) {
            LineGroup(listOf(payload), booleanArrayOf(true))
        }
        entry.group = group
        if (group.state == LineGroup.State.IDLE) group.start(coordinator)
        return entry
    }

    /** True while [entity] is close enough to be read, given its own glyph height. */
    private fun reachable(entity: Display.TextDisplay): Boolean {
        val player = Minecraft.getInstance().player ?: return false
        val state = entity.renderState() ?: return false

        val scale = state.transformation().get(1.0f).scale().y()
        val range = abs(scale) * GLYPH_HEIGHT * RANGE_FACTOR
        if (range <= 0.0f) return false

        val limit = range.toDouble()
        return entity.distanceToSqr(player) <= limit * limit
    }

    /** Splits the translated text exactly like the renderer splits the original. */
    private fun layout(segments: List<StyledSegment>, text: String, width: Int): Display.TextDisplay.CachedInfo {
        val component = StyledText.rebuild(text, segments) ?: Component.literal(StyledText.stripMarkers(text))
        val font = Minecraft.getInstance().font
        val lines = font.split(component, width)
        val cached = ArrayList<Display.TextDisplay.CachedLine>(lines.size)
        var maxWidth = 0

        for (line in lines) {
            val lineWidth = font.width(line)
            if (lineWidth > maxWidth) maxWidth = lineWidth
            cached.add(Display.TextDisplay.CachedLine(line, lineWidth))
        }

        return Display.TextDisplay.CachedInfo(cached, maxWidth)
    }

    private companion object {
        /** Height of one text line in blocks: 10 pixels drawn at the renderer's 0.025 scale. */
        const val GLYPH_HEIGHT = 0.25f

        /** How many glyph heights away a display is still translated (20 blocks at scale 1). */
        const val RANGE_FACTOR = 80.0f

        /** Vanilla default of the `line_width` data value. */
        const val DEFAULT_LINE_WIDTH = 200

        const val MAX_GROUPS = 256
    }
}
