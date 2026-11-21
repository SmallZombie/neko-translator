package me.avgt.s9e.client.item

import java.util.IdentityHashMap
import me.avgt.s9e.client.chat.StyledSegment
import me.avgt.s9e.client.chat.StyledText
import me.avgt.s9e.client.lang.LanguageKeys
import me.avgt.s9e.client.mixin.AbstractContainerScreenAccess
import me.avgt.s9e.client.util.Feed
import me.avgt.s9e.common.config.NekoConfig
import me.avgt.s9e.common.text.NekoText
import me.avgt.s9e.common.translate.TranslationCoordinator
import me.avgt.s9e.common.translate.TranslationResult
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.contents.PlainTextContents
import net.minecraft.network.chat.contents.TranslatableContents
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack

/**
 * Translation state of item names and their tooltip descriptions.
 *
 * <p>One item is one unit: the name line and every description line are translated together,
 * shown together and switched back together. The coloured `[T]` behind the name describes
 * that unit (grey = original, yellow = working, green = translation shown, red = failed);
 * description lines never get a marker of their own.
 *
 * <p>An item that the client already shows in the target language is left completely alone:
 * no `[T]`, no request. A line counts as translated when all of its text comes from keys the
 * current client language itself defines (see [LanguageKeys]) - text that only fell back to
 * `en_us`, custom names and lore do not count, so they are still translated.
 *
 * <p>Each line is translated on its own request so it keeps its own formatting and shares the
 * translation cache with chat. An item is identified by the text of its whole tooltip, which
 * means stacks that only differ by count or durability share one state. The state lives in
 * memory only: after a restart the first hover hits the ordinary cache again.
 */
class ItemTranslator(
    private val config: NekoConfig,
    private val coordinator: TranslationCoordinator,
    private val language: () -> String,
) {
    enum class State { IDLE, TRANSLATING, DONE, FAILED }

    /** One tooltip line together with the styled segments it is built from. */
    private class Line(val component: Component, val segments: List<StyledSegment>, val needsTranslation: Boolean) {
        val payload: String = StyledText.payload(segments)
        val requestable: Boolean = needsTranslation && payload.isNotBlank()
    }

    private class Parsed(val lines: List<Line>, val payloads: List<String>, val key: String)

    /** Tooltip lines of one stack, cached because the HUD asks for them on every frame. */
    private class Snapshot(val parsed: Parsed, val advanced: Boolean, val language: String, val generation: Int)

    private class Entry(val payloads: List<String>, val requestable: BooleanArray) {
        val translations: Array<String?> = arrayOfNulls(payloads.size)
        var state: State = State.IDLE
        var showingOriginal: Boolean = false
    }

    private val entries = object : LinkedHashMap<String, Entry>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>): Boolean =
            size > MAX_ENTRIES
    }

    private val snapshots = IdentityHashMap<ItemStack, Snapshot>()

    /**
     * Set while this class builds a tooltip itself (hotbar name, hover check, key press). The
     * tooltip mixin must not decorate those throw-away lists: the result would end up inside
     * the text that identifies the item.
     */
    private val synthetic = ThreadLocal.withInitial { false }

    private var lastHeld: ItemStack? = null
    private var lastHeldSlot: Int = -1
    private var hoverSlot: Slot? = null
    private var hoverStack: ItemStack? = null

    /** Adds the `[T]` marker and, when available, the translation to a tooltip. */
    fun decorate(lines: List<Component>): List<Component> {
        if (synthetic.get() || !config.enabled || lines.isEmpty()) return lines

        val minecraft = Minecraft.getInstance()
        if (!minecraft.isSameThread) return lines
        if (minecraft.screen !is AbstractContainerScreen<*>) return lines

        val parsed = parse(lines, language())
        if (!parsed.hasWork()) return lines

        val entry = entries[parsed.key]
        val result = ArrayList<Component>(parsed.lines.size)

        for ((index, line) in parsed.lines.withIndex()) {
            result.add(translated(entry, line, index) ?: line.component)
        }

        result[0] = result[0].copy().append(Component.literal(" ")).append(marker(entry))
        return result
    }

    /** The item name shown above the hotbar: translated when possible, with the marker when needed. */
    fun hudName(stack: ItemStack, original: Component): Component {
        if (!config.enabled || stack.isEmpty()) return original

        val parsed = snapshot(stack).parsed
        if (!parsed.hasWork()) return original

        val entry = entries[parsed.key]
        val name = parsed.lines.firstOrNull()?.let { translated(entry, it, 0) } ?: original
        return name.copy().append(Component.literal(" ")).append(marker(entry))
    }

    /** Triggered once per client tick: noticed hovers and the item held in the main hand. */
    fun tick() {
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player
        if (player == null) {
            clearHover()
            return
        }

        if (!config.enabled) {
            clearHover()
            lastHeld = null
            lastHeldSlot = -1
            return
        }

        trackHeld(player)

        if (!config.autoTranslate) {
            clearHover()
            return
        }

        trackHover()
    }

    /** Handles the `translate item` key: translate, show the original, or retry. */
    fun toggleHovered(): Boolean {
        if (!config.enabled) return false

        val slot = hoveredSlot() ?: return false
        if (!slot.hasItem()) return false

        val stack = slot.item
        if (stack.isEmpty()) return false

        val parsed = snapshot(stack).parsed
        if (!parsed.hasWork()) return false

        val entry = entryOf(parsed)
        when (entry.state) {
            State.IDLE -> start(entry)
            // While a translation is running a click does nothing on purpose, like the chat marker.
            State.TRANSLATING -> Unit
            State.DONE -> entry.showingOriginal = !entry.showingOriginal
            State.FAILED -> {
                for (index in entry.requestable.indices) {
                    if (entry.requestable[index]) coordinator.forgetFailure(entry.payloads[index])
                }
                start(entry)
            }
        }

        return true
    }

    private fun trackHeld(player: Player) {
        val inventory = player.inventory
        val slot = inventory.selectedSlot
        val stack = inventory.getItem(slot)
        if (stack === lastHeld && slot == lastHeldSlot) return

        lastHeld = stack
        lastHeldSlot = slot
        if (!config.autoTranslate || stack.isEmpty()) return

        startIfIdle(stack)
    }

    private fun trackHover() {
        val slot = hoveredSlot()
        val stack = slot?.item?.takeIf { !it.isEmpty() }
        if (stack == null) {
            clearHover()
            return
        }

        if (slot === hoverSlot && stack === hoverStack) return
        hoverSlot = slot
        hoverStack = stack

        startIfIdle(stack)
    }

    private fun startIfIdle(stack: ItemStack) {
        val parsed = snapshot(stack).parsed
        if (!parsed.hasWork()) return

        val entry = entryOf(parsed)
        if (entry.state == State.IDLE) start(entry)
    }

    private fun clearHover() {
        hoverSlot = null
        hoverStack = null
    }

    private fun hoveredSlot(): Slot? {
        val screen = Minecraft.getInstance().screen
        if (screen !is AbstractContainerScreen<*>) return null
        return (screen as AbstractContainerScreenAccess).nekoHoveredSlot()
    }

    private fun entryOf(parsed: Parsed): Entry = entries.getOrPut(parsed.key) {
        Entry(parsed.payloads, BooleanArray(parsed.lines.size) { parsed.lines[it].requestable })
    }

    private fun start(entry: Entry) {
        if (entry.state == State.TRANSLATING) return

        val indexes = entry.requestable.indices.filter { entry.requestable[it] }
        entry.translations.fill(null)
        entry.showingOriginal = false

        if (indexes.isEmpty()) {
            entry.state = State.DONE
            return
        }

        entry.state = State.TRANSLATING
        var remaining = indexes.size
        var failure: NekoText? = null

        for (index in indexes) {
            coordinator.requestForChat(entry.payloads[index]).whenComplete { result, _ ->
                Minecraft.getInstance().execute {
                    when (result) {
                        is TranslationResult.Ok -> entry.translations[index] = result.text
                        is TranslationResult.Err -> if (failure == null) failure = result.message
                        null -> if (failure == null) failure = NekoText.of("neko-translator.error.translate_failed")
                    }

                    remaining--
                    if (remaining == 0) {
                        val message = failure
                        entry.state = if (message == null) State.DONE else State.FAILED
                        if (message != null) Feed.error(message)
                    }
                }
            }
        }
    }

    private fun parse(components: List<Component>, language: String): Parsed {
        // A translation is only pointless when the target is what the client already shows.
        val skipLocalized = language == clientLanguage()
        val lines = components.map { component ->
            Line(component, StyledText.flatten(component), !skipLocalized || hasForeignText(component))
        }
        val payloads = lines.map { it.payload }
        return Parsed(lines, payloads, keyOf(language, payloads))
    }

    private fun clientLanguage(): String = Minecraft.getInstance().options.languageCode.lowercase()

    /**
     * True when the line still contains text the client language has no translation for: a
     * literal (custom name, lore, text a mod built by hand) or a key only `en_us` provides.
     */
    private fun hasForeignText(component: Component): Boolean {
        when (val contents = component.contents) {
            is TranslatableContents -> {
                if (!LanguageKeys.isLocalized(contents.key)) return true
                for (arg in contents.args) {
                    if (arg is Component && hasForeignText(arg)) return true
                }
            }

            is PlainTextContents -> if (contents.text().any { it.isLetter() }) return true

            // Score, selector, keybind and NBT text is never part of a language file.
            else -> if (component.string.any { it.isLetter() }) return true
        }

        for (sibling in component.siblings) {
            if (hasForeignText(sibling)) return true
        }

        return false
    }

    /**
     * Identifies an item by everything the player reads. The target language and the loaded
     * language index are part of the key, so a language or resource reload starts over.
     */
    private fun keyOf(language: String, payloads: List<String>): String = buildString {
        append(LanguageKeys.generation)
        append('\u0000')
        append(language)
        for (payload in payloads) {
            append('\u0000')
            append(payload)
        }
    }

    private fun snapshot(stack: ItemStack): Snapshot {
        val minecraft = Minecraft.getInstance()
        val advanced = minecraft.options.advancedItemTooltips
        val language = language()
        val generation = LanguageKeys.generation

        snapshots[stack]?.let {
            if (it.advanced == advanced && it.language == language && it.generation == generation) return it
        }

        val parsed = withoutDecoration { parse(Screen.getTooltipFromItem(minecraft, stack), language) }
        if (snapshots.size >= MAX_SNAPSHOTS) snapshots.clear()
        return Snapshot(parsed, advanced, language, generation).also { snapshots[stack] = it }
    }

    private fun <T> withoutDecoration(block: () -> T): T {
        val previous = synthetic.get()
        synthetic.set(true)
        try {
            return block()
        } finally {
            synthetic.set(previous)
        }
    }

    /** The translated component of one line, or {@code null} when the original has to be shown. */
    private fun translated(entry: Entry?, line: Line, index: Int): Component? {
        if (entry == null || entry.state != State.DONE || entry.showingOriginal) return null
        if (!line.requestable) return null

        val text = entry.translations.getOrNull(index) ?: return null
        if (text.isBlank()) return null

        // The model did not keep the segment markers; show the plain text, like a chat line.
        return StyledText.rebuild(text, line.segments) ?: Component.literal(StyledText.stripMarkers(text))
    }

    private fun marker(entry: Entry?): Component {
        val color = when (entry?.state) {
            State.TRANSLATING -> ChatFormatting.YELLOW
            State.DONE -> if (entry.showingOriginal) ChatFormatting.GRAY else ChatFormatting.GREEN
            State.FAILED -> ChatFormatting.RED
            null, State.IDLE -> ChatFormatting.GRAY
        }

        return Component.literal(MARKER).withStyle(Style.EMPTY.withColor(color))
    }

    private fun Parsed.hasWork(): Boolean = lines.any { it.requestable }

    private companion object {
        const val MARKER = "[T]"
        const val MAX_ENTRIES = 256
        const val MAX_SNAPSHOTS = 256
    }
}
