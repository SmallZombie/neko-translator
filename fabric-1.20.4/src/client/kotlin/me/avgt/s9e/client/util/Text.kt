package me.avgt.s9e.client.util

import me.avgt.s9e.common.text.NekoText
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent

/**
 * Renders a [NekoText] with the language the client currently uses. A nested [NekoText] becomes
 * a nested component, which is what the vanilla translatable machinery expects for `%s` args.
 */
fun NekoText.toComponent(): MutableComponent = Component.translatable(
    key,
    *args.map { if (it is NekoText) it.toComponent() else it }.toTypedArray(),
)
