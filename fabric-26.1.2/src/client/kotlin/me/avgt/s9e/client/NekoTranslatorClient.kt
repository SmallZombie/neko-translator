package me.avgt.s9e.client

import me.avgt.s9e.client.command.NekoCommands
import me.avgt.s9e.client.gui.NekoScreens
import me.avgt.s9e.net.RelayPayloads
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.gui.components.ChatComponent
import net.minecraft.client.gui.screens.inventory.BookViewScreen
import net.minecraft.client.multiplayer.chat.GuiMessageSource
import net.minecraft.network.chat.Component

object NekoTranslatorClient : ClientModInitializer {
    override fun onInitializeClient() {
        Neko.init()

        ClientPlayNetworking.registerGlobalReceiver(RelayPayloads.ToClient.TYPE) { payload, _ ->
            Neko.peers.handleIncoming(payload.from(), payload.data())
        }

        ClientPlayNetworking.registerGlobalReceiver(RelayPayloads.Failed.TYPE) { payload, _ ->
            Neko.peers.handleRelayFailure(payload.token())
        }

        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ -> NekoCommands.register(dispatcher) }

        ClientTickEvents.END_CLIENT_TICK.register { NekoScreens.tick() }

        ClientLifecycleEvents.CLIENT_STOPPING.register { Neko.shutdown() }
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
