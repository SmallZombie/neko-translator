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
    fun afterChatAdd(chat: ChatComponent) {
        Neko.chat.afterAdd(chat)
    }

    /** Called when the player clicks a `[T]` marker. */
    @JvmStatic
    fun onMarkerClick(id: Long) {
        Neko.chat.onClick(id)
    }
}
