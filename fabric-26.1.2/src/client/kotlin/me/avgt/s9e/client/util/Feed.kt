package me.avgt.s9e.client.util

import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

/** Small helpers for printing status lines into the vanilla chat HUD. */
object Feed {
    private val PREFIX: Component = Component.literal("[Neko Translator] ").withStyle(ChatFormatting.AQUA)

    fun infoComponent(message: String): Component =
        PREFIX.copy().append(Component.literal(message).withStyle(ChatFormatting.GRAY))

    fun errorComponent(message: String): Component =
        PREFIX.copy()
            .append(Component.literal("[ERR] ").withStyle(ChatFormatting.RED))
            .append(Component.literal(message).withStyle(ChatFormatting.RED))

    fun info(message: String) = send(infoComponent(message))

    fun error(message: String) = send(errorComponent(message))

    private fun send(component: Component) {
        val minecraft = Minecraft.getInstance()
        if (!minecraft.isSameThread) {
            minecraft.execute { send(component) }
            return
        }

        minecraft.gui.chat.addClientSystemMessage(component)
    }
}
