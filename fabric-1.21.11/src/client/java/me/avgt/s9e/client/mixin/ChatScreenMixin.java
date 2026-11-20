package me.avgt.s9e.client.mixin;

import java.util.Optional;

import me.avgt.s9e.client.NekoTranslatorClient;
import me.avgt.s9e.client.chat.ChatTranslator;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Handles clicks on the [T] marker instead of forwarding a custom click action to the server. */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
	@Inject(method = "handleComponentClicked", at = @At("HEAD"), cancellable = true)
	private void neko$handleComponentClicked(final Style style, final boolean allowInsertions, final CallbackInfoReturnable<Boolean> callback) {
		if (style == null) return;

		ClickEvent event = style.getClickEvent();
		if (!(event instanceof ClickEvent.Custom custom)) return;
		if (!custom.id().equals(ChatTranslator.TRANSLATE_CLICK_ID)) return;

		Optional<Tag> payload = custom.payload();
		if (payload.isEmpty() || !(payload.get() instanceof StringTag tag)) return;

		long id;
		try {
			id = Long.parseLong(tag.value());
		} catch (NumberFormatException ignored) {
			return;
		}

		NekoTranslatorClient.onMarkerClick(id);
		callback.setReturnValue(true);
		callback.cancel();
	}
}
