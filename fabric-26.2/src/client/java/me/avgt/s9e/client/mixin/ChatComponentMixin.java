package me.avgt.s9e.client.mixin;

import me.avgt.s9e.client.NekoTranslatorClient;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Appends the clickable [T] marker to every chat message before it is stored. */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
	@ModifyVariable(method = "addMessage", at = @At("HEAD"), argsOnly = true, index = 1)
	private Component neko$decorate(final Component contents) {
		return NekoTranslatorClient.decorateChat(contents);
	}

	@Inject(method = "addMessage", at = @At("TAIL"))
	private void neko$afterAdd(
			final Component contents,
			final MessageSignature signature,
			final GuiMessageSource source,
			final GuiMessageTag tag,
			final CallbackInfo callbackInfo) {
		NekoTranslatorClient.afterChatAdd((ChatComponent) (Object) this, source);
	}
}
