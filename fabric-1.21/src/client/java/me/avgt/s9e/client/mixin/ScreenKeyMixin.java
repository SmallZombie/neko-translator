package me.avgt.s9e.client.mixin;

import me.avgt.s9e.client.NekoTranslatorClient;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Handles the "translate item" key while a screen is open. Vanilla only feeds key mappings to
 * the game while no screen is open, so the binding has to be matched here.
 */
@Mixin(Screen.class)
public abstract class ScreenKeyMixin {
	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void neko$itemKeyPressed(
			final int keyCode,
			final int scanCode,
			final int modifiers,
			final CallbackInfoReturnable<Boolean> callbackInfo) {
		if (NekoTranslatorClient.onItemKeyPress((Screen) (Object) this, keyCode, scanCode)) {
			callbackInfo.setReturnValue(true);
		}
	}
}
