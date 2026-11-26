package me.avgt.s9e.client

import me.avgt.s9e.client.command.NekoCommands
import me.avgt.s9e.client.gui.NekoScreens
import me.avgt.s9e.net.RelayPayloads
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.KeyMapping
import net.minecraft.client.gui.components.AbstractTextAreaWidget
import net.minecraft.client.gui.components.ChatComponent
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.BookViewScreen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.multiplayer.chat.GuiMessageSource
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.util.FormattedCharSequence
import net.minecraft.world.entity.Display
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.SignText

object NekoTranslatorClient : ClientModInitializer {
    /** Translates or restores the item under the mouse while a container screen is open. */
    private lateinit var translateItemKey: KeyMapping

    /** Translates or restores the text of the sign the crosshair is on. */
    private lateinit var translateSignKey: KeyMapping

    /** Puts the original text of the sign the crosshair is on on the clipboard. */
    private lateinit var copySignKey: KeyMapping

    /** Switches the visible boss bar names between original and translation. */
    private lateinit var translateBossbarKey: KeyMapping

    /** Translates or restores the name tag of the entity the crosshair is on. */
    private lateinit var translateEntityNameKey: KeyMapping

    /** The key is only handled while a screen is open, so its repeat and release are tracked by hand. */
    private var itemKeyScreen: Screen? = null
    private var itemKeyDown = false

    /** GLFW keycode of `T`, the same physical key vanilla uses for chat. */
    private const val DEFAULT_ITEM_KEY = 84

    /** GLFW keycode of `G`. */
    private const val DEFAULT_SIGN_KEY = 71

    /** GLFW keycode of `G`. The crosshair rests on a sign or on an entity, never on both. */
    private const val DEFAULT_ENTITY_NAME_KEY = 71

    /** GLFW keycode of `;`. */
    private const val DEFAULT_COPY_KEY = 59

    /** GLFW keycode of `B`. */
    private const val DEFAULT_BOSSBAR_KEY = 66

    override fun onInitializeClient() {
        Neko.init()

        val category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("neko-translator", "main"))

        translateItemKey = KeyMappingHelper.registerKeyMapping(
            KeyMapping("key.neko-translator.translate_item", DEFAULT_ITEM_KEY, category)
        )

        translateSignKey = KeyMappingHelper.registerKeyMapping(
            KeyMapping("key.neko-translator.translate_sign", DEFAULT_SIGN_KEY, category)
        )

        copySignKey = KeyMappingHelper.registerKeyMapping(
            KeyMapping("key.neko-translator.copy_sign", DEFAULT_COPY_KEY, category)
        )

        translateBossbarKey = KeyMappingHelper.registerKeyMapping(
            KeyMapping("key.neko-translator.translate_bossbar", DEFAULT_BOSSBAR_KEY, category)
        )

        translateEntityNameKey = KeyMappingHelper.registerKeyMapping(
            KeyMapping("key.neko-translator.translate_entity_name", DEFAULT_ENTITY_NAME_KEY, category)
        )

        ClientPlayNetworking.registerGlobalReceiver(RelayPayloads.ToClient.TYPE) { payload, _ ->
            Neko.peers.handleIncoming(payload.from(), payload.data())
        }

        ClientPlayNetworking.registerGlobalReceiver(RelayPayloads.Failed.TYPE) { payload, _ ->
            Neko.peers.handleRelayFailure(payload.token())
        }

        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ -> NekoCommands.register(dispatcher) }

        ClientTickEvents.END_CLIENT_TICK.register {
            NekoScreens.tick()
            Neko.items.tick()
            Neko.signs.tick()
            Neko.names.tick()

            // Key events reach a mapping only while no screen is open, so the sign and entity
            // keys cannot collide with the item key that lives inside container screens.
            if (translateSignKey.consumeClick()) Neko.signs.toggleAimed()
            if (translateEntityNameKey.consumeClick()) Neko.names.toggleAimed()
            if (copySignKey.consumeClick()) Neko.signs.copyAimed()
            if (translateBossbarKey.consumeClick()) Neko.hud.toggleBossbar()
        }

        ClientLifecycleEvents.CLIENT_STOPPING.register { Neko.shutdown() }
    }

    /** Called by the HUD mixin for the title, the subtitle and the action bar message. */
    @JvmStatic
    fun hudText(component: Component): Component =
        if (Neko.ready) Neko.hud.title(component) else component

    /** Called by the boss bar mixin for the name of one boss bar. */
    @JvmStatic
    fun bossbarText(component: Component): Component =
        if (Neko.ready) Neko.hud.bossbar(component) else component

    /** Called by the boss bar mixin at the start of a frame. */
    @JvmStatic
    fun beginBossbarFrame() {
        if (Neko.ready) Neko.hud.beginBossbarFrame()
    }

    /** Called by the [ItemStack] mixin to build the decorated tooltip of an item. */
    @JvmStatic
    fun itemTooltip(lines: List<Component>): List<Component> =
        if (Neko.ready) Neko.items.decorate(lines) else lines

    /** Called by the HUD mixin for the name that vanilla shows above the hotbar. */
    @JvmStatic
    fun itemName(stack: ItemStack, original: Component): Component =
        if (Neko.ready) Neko.items.hudName(stack, original) else original

    /** Called by the sign renderer mixin to build the lines that a sign draws. */
    @JvmStatic
    fun signLines(
        signText: SignText,
        filtered: Boolean,
        original: Array<FormattedCharSequence>,
        prepare: java.util.function.Function<Component, FormattedCharSequence>,
    ): Array<FormattedCharSequence> =
        if (Neko.ready) Neko.signs.display(signText, filtered, original) { prepare.apply(it) } else original

    /** Called by the entity renderer to build the name tag that is drawn above an entity. */
    @JvmStatic
    fun entityName(component: Component): Component =
        if (Neko.ready) Neko.names.nameTag(component) else component

    /** Called by the text display renderer to replace the layout of a translated display. */
    @JvmStatic
    fun textDisplayInfo(
        entity: Display.TextDisplay,
        splitter: Display.TextDisplay.LineSplitter,
    ): Display.TextDisplay.CachedInfo =
        if (Neko.ready) Neko.displays.info(entity, splitter) else entity.cacheDisplay(splitter)

    /**
     * Called by the [Screen] mixin before a screen handles a key. Returns true when the key was
     * the item binding and was consumed.
     */
    @JvmStatic
    fun onItemKeyPress(screen: Screen, event: KeyEvent): Boolean {
        if (!Neko.ready) return false
        if (screen !== itemKeyScreen) {
            itemKeyScreen = screen
            itemKeyDown = false
        }

        if (screen !is AbstractContainerScreen<*>) return false
        if (!translateItemKey.matches(event)) return false
        // Holding the key must not toggle the item on every auto-repeat.
        if (itemKeyDown) return true

        val focused = screen.focused
        if (focused is EditBox || focused is AbstractTextAreaWidget) return false

        itemKeyDown = true
        return Neko.items.toggleHovered()
    }

    @JvmStatic
    fun onItemKeyRelease(event: KeyEvent) {
        if (!Neko.ready) return
        if (translateItemKey.matches(event)) itemKeyDown = false
    }

    /** Called by the chat mixin before a message is stored in the chat history. */
    @JvmStatic
    fun decorateChat(contents: Component): Component = Neko.chat.decorate(contents)

    /** Called by the chat mixin after a message was stored in the chat history. */
    @JvmStatic
    fun afterChatAdd(chat: ChatComponent, source: GuiMessageSource) {
        Neko.chat.afterAdd(chat, source)
    }

    /** Called when the player clicks a `[T]` marker. */
    @JvmStatic
    fun onMarkerClick(id: Long) {
        Neko.chat.onClick(id)
    }

    // --- book screen -------------------------------------------------------------------

    /** Whether the book translate button is shown at all (follows the global `[T]` switch). */
    @JvmStatic
    fun bookVisible(): Boolean = Neko.books.visible()

    /** Called before a book frame is drawn; notices page turns and starts auto translation. */
    @JvmStatic
    fun onBookFrame(screen: BookViewScreen, page: Int, original: Component) {
        Neko.books.onFrame(screen, page, original)
    }

    @JvmStatic
    fun bookVersion(screen: BookViewScreen): Int = Neko.books.version(screen)

    /** Replaces the page text with its translation while the page is shown translated. */
    @JvmStatic
    fun bookPage(screen: BookViewScreen, original: Component): Component = Neko.books.display(screen, original)

    @JvmStatic
    fun onBookButton(screen: BookViewScreen) {
        Neko.books.click(screen)
    }

    @JvmStatic
    fun bookColor(screen: BookViewScreen): Int = Neko.books.markerColor(screen)
}
