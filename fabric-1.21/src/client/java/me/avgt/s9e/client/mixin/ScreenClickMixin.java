package me.avgt.s9e.client.mixin;

import me.avgt.s9e.client.NekoTranslatorClient;
import me.avgt.s9e.client.chat.ChatTranslator;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Handles clicks on the [T] marker instead of letting the screen send the command to the
 * server. 1.21 has no custom click action, so the marker carries a command that only exists
 * on the client and is consumed here.
 */
@Mixin(Screen.class)
public abstract class ScreenClickMixin {
	@Inject(method = "handleComponentClicked", at = @At("HEAD"), cancellable = true)
	private void neko$handleMarkerClick(final Style style, final CallbackInfoReturnable<Boolean> callback) {
		if (style == null) return;

		ClickEvent event = style.getClickEvent();
		if (event == null || event.getAction() != ClickEvent.Action.RUN_COMMAND) return;

		String value = event.getValue();
		if (!value.startsWith(ChatTranslator.CLICK_COMMAND)) return;

		long id;
		try {
			id = Long.parseLong(value.substring(ChatTranslator.CLICK_COMMAND.length()));
		} catch (NumberFormatException ignored) {
			return;
		}

		NekoTranslatorClient.onMarkerClick(id);
		callback.setReturnValue(true);
	}
}
