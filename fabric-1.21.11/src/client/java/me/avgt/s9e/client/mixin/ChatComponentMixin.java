package me.avgt.s9e.client.mixin;

import me.avgt.s9e.client.NekoTranslatorClient;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Appends the clickable [T] marker to every chat message before it is stored.
 *
 * <p>1.21.11 has two overloads instead of a {@code GuiMessageSource}: the three argument
 * one for server chat and the one argument one that both server system chat (`/say`,
 * server notices) and client side text (vanilla hints) use. There is no cheap way to tell
 * those apart, so every message is treated as a chat line here; only messages that start
 * with our own prefix are skipped by the decorator.
 */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
	private static final String ADD_MESSAGE =
			"addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V";

	@ModifyVariable(method = ADD_MESSAGE, at = @At("HEAD"), argsOnly = true, index = 1)
	private Component neko$decorate(final Component contents) {
		return NekoTranslatorClient.decorateChat(contents);
	}

	@Inject(method = ADD_MESSAGE, at = @At("TAIL"))
	private void neko$afterAdd(
			final Component contents,
			final MessageSignature signature,
			final GuiMessageTag tag,
			final CallbackInfo callbackInfo) {
		NekoTranslatorClient.afterChatAdd((ChatComponent) (Object) this);
	}
}
