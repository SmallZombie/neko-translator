package me.avgt.s9e.client.item

import java.util.IdentityHashMap
import me.avgt.s9e.client.chat.StyledSegment
import me.avgt.s9e.client.chat.StyledText
import me.avgt.s9e.client.lang.LanguageKeys
import me.avgt.s9e.client.mixin.AbstractContainerScreenAccess
import me.avgt.s9e.client.translate.LineGroup
import me.avgt.s9e.common.config.NekoConfig
import me.avgt.s9e.common.translate.TranslationCoordinator
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.MerchantScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.MerchantMenu
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
    /** One tooltip line together with the styled segments it is built from. */
    private class Line(val component: Component, val segments: List<StyledSegment>, val needsTranslation: Boolean) {
        val payload: String = StyledText.payload(segments)
        val requestable: Boolean = needsTranslation && StyledText.isTranslatable(payload)
    }

    private class Parsed(val lines: List<Line>, val payloads: List<String>, val key: String) {
        val hasWork: Boolean = lines.any { it.requestable }
    }

    /** Tooltip lines of one stack, cached because the HUD asks for them on every frame. */
    private class Snapshot(val parsed: Parsed, val advanced: Boolean, val language: String, val generation: Int)

    private val entries = object : LinkedHashMap<String, LineGroup>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LineGroup>): Boolean =
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

    /** The merchant screen whose offers were already queued, and how many offers it had then. */
    private var merchant: MerchantScreen? = null
    private var merchantOffers = 0

    /** Adds the `[T]` marker and, when available, the translation to a tooltip. */
    fun decorate(lines: List<Component>): List<Component> {
        if (synthetic.get() || !config.enabled || lines.isEmpty()) return lines

        val minecraft = Minecraft.getInstance()
        if (!minecraft.isSameThread) return lines
        if (minecraft.gui.screen() !is AbstractContainerScreen<*>) return lines

        val parsed = parse(lines, language())
        if (!parsed.hasWork) return lines

        val entry = entries[parsed.key]
        val result = ArrayList<Component>(parsed.lines.size)

        for ((index, line) in parsed.lines.withIndex()) {
            result.add(translated(entry, line, index) ?: line.component)
        }

        result[0] = result[0].copy().append(Component.literal(" ")).append(entry?.marker() ?: LineGroup.idleMarker())
        return result
    }

    /** The item name shown above the hotbar: translated when possible, with the marker when needed. */
    fun hudName(stack: ItemStack, original: Component): Component {
        if (!config.enabled || stack.isEmpty()) return original

        val parsed = snapshot(stack).parsed
        if (!parsed.hasWork) return original

        val entry = entries[parsed.key]
        val name = parsed.lines.firstOrNull()?.let { translated(entry, it, 0) } ?: original
        return name.copy().append(Component.literal(" ")).append(entry?.marker() ?: LineGroup.idleMarker())
    }

    /** Triggered once per client tick: notices hovers, the held item and an open merchant screen. */
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
        preloadMerchant(minecraft, player)
    }

    /** Handles the `translate item` key: translate, show the original, or retry. */
    fun toggleHovered(): Boolean {
        if (!config.enabled) return false

        val slot = hoveredSlot() ?: return false
        if (!slot.hasItem()) return false

        val stack = slot.item
        if (stack.isEmpty()) return false

        val parsed = snapshot(stack).parsed
        if (!parsed.hasWork) return false

        entryOf(parsed).toggle(coordinator)
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
        if (stack.isEmpty) return

        val parsed = snapshot(stack).parsed
        if (!parsed.hasWork) return

        val entry = entryOf(parsed)
        if (entry.state == LineGroup.State.IDLE) entry.start(coordinator)
    }

    private fun clearHover() {
        hoverSlot = null
        hoverStack = null
    }

    /**
     * Queues every offer of an open merchant screen, so the trade list is translated without
     * hovering it. The list grows when the villager levels up, so it is read again when the
     * offer count changes.
     */
    private fun preloadMerchant(minecraft: Minecraft, player: Player) {
        val screen = minecraft.gui.screen()
        if (screen !is MerchantScreen) {
            merchant = null
            return
        }

        val menu = player.containerMenu as? MerchantMenu ?: return
        val offers = menu.offers
        if (screen === merchant && offers.size == merchantOffers) return

        merchant = screen
        merchantOffers = offers.size
        for (offer in offers) {
            startIfIdle(offer.costA)
            startIfIdle(offer.costB)
            startIfIdle(offer.result)
        }
    }

    private fun hoveredSlot(): Slot? {
        val screen = Minecraft.getInstance().gui.screen()
        if (screen !is AbstractContainerScreen<*>) return null
        return (screen as AbstractContainerScreenAccess).nekoHoveredSlot()
    }

    private fun entryOf(parsed: Parsed): LineGroup = entries.getOrPut(parsed.key) {
        LineGroup(parsed.payloads, BooleanArray(parsed.lines.size) { parsed.lines[it].requestable })
    }

    private fun parse(components: List<Component>, language: String): Parsed {
        // A translation is only pointless when the target is what the client already shows.
        val skipLocalized = language == LanguageKeys.clientLanguage()
        val lines = components.map { component ->
            Line(component, StyledText.flatten(component), !skipLocalized || LanguageKeys.hasForeignText(component))
        }
        val payloads = lines.map { it.payload }
        return Parsed(lines, payloads, LineGroup.key(language, payloads))
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
    private fun translated(entry: LineGroup?, line: Line, index: Int): Component? {
        val text = entry?.translation(index) ?: return null

        // The model did not keep the segment markers; show the plain text, like a chat line.
        return StyledText.rebuild(text, line.segments) ?: Component.literal(StyledText.stripMarkers(text))
    }

    private companion object {
        const val MAX_ENTRIES = 256
        const val MAX_SNAPSHOTS = 256
    }
}
