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
import net.minecraft.world.item.ItemStack

object NekoTranslatorClient : ClientModInitializer {
    /** Translates or restores the item under the mouse while a container screen is open. */
    private lateinit var translateItemKey: KeyMapping

    /** The key is only handled while a screen is open, so its repeat and release are tracked by hand. */
    private var itemKeyScreen: Screen? = null
    private var itemKeyDown = false

    /** SDL scancode of `T`, the same physical key vanilla uses for chat. */
    private const val DEFAULT_ITEM_KEY = 23

    override fun onInitializeClient() {
        Neko.init()

        translateItemKey = KeyMappingHelper.registerKeyMapping(
            KeyMapping(
                "key.neko-translator.translate_item",
                DEFAULT_ITEM_KEY,
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath("neko-translator", "main")),
            )
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
        }

        ClientLifecycleEvents.CLIENT_STOPPING.register { Neko.shutdown() }
    }

    /** Called by the [ItemStack] mixin to build the decorated tooltip of an item. */
    @JvmStatic
    fun itemTooltip(lines: List<Component>): List<Component> =
        if (Neko.ready) Neko.items.decorate(lines) else lines

    /** Called by the HUD mixin for the name that vanilla shows above the hotbar. */
    @JvmStatic
    fun itemName(stack: ItemStack, original: Component): Component =
        if (Neko.ready) Neko.items.hudName(stack, original) else original

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
