package me.avgt.s9e.client.gui

import me.avgt.s9e.common.log.NekoLog
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen

/**
 * Opens screens on the next client tick.
 *
 * <p>Screens must not be opened directly from a chat command: vanilla closes the chat screen
 * right after the command was dispatched (`ChatScreen#keyPressed` calls `handleChatInput` and
 * then `setScreen(null)`), which would immediately discard the new screen. Deferring the open
 * to the next tick avoids that.
 */
object NekoScreens {
    private var pending: (() -> Screen)? = null

    fun open(factory: () -> Screen) {
        pending = factory
    }

    fun tick() {
        val factory = pending ?: return
        pending = null

        try {
            Minecraft.getInstance().gui.setScreen(factory())
        } catch (t: Throwable) {
            NekoLog.warn("Failed to open screen: ${t.javaClass.simpleName}: ${t.message}")
        }
    }
}
