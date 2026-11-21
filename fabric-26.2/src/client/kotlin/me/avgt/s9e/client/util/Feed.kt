package me.avgt.s9e.client.util

import me.avgt.s9e.common.text.NekoText
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

/** Small helpers for printing status lines into the vanilla chat HUD. */
object Feed {
    private val PREFIX: Component = Component.literal("[Neko Translator] ").withStyle(ChatFormatting.AQUA)

    fun infoComponent(message: NekoText): Component =
        PREFIX.copy().append(message.toComponent().withStyle(ChatFormatting.GRAY))

    fun errorComponent(message: NekoText): Component =
        PREFIX.copy()
            .append(Component.literal("[ERR] ").withStyle(ChatFormatting.RED))
            .append(message.toComponent().withStyle(ChatFormatting.RED))

    fun info(message: NekoText) = send(infoComponent(message))

    fun error(message: NekoText) = send(errorComponent(message))

    private fun send(component: Component) {
        val minecraft = Minecraft.getInstance()
        if (!minecraft.isSameThread) {
            minecraft.execute { send(component) }
            return
        }

        minecraft.gui.hud.chat.addClientSystemMessage(component)
    }
}
