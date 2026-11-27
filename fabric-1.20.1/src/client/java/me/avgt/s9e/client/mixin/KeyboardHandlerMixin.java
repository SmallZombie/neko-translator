package me.avgt.s9e.client.mixin;

import me.avgt.s9e.client.NekoTranslatorClient;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Notices the release of the "translate item" key. Screens only receive presses, so without
 * this the auto-repeat of a held key could not be told apart from a new press.
 */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
	@Inject(method = "keyPress", at = @At("HEAD"))
	private void neko$itemKeyReleased(
			final long window,
			final int key,
			final int scanCode,
			final int action,
			final int modifiers,
			final CallbackInfo callbackInfo) {
		if (action == 0) {
			NekoTranslatorClient.onItemKeyRelease(key, scanCode);
		}
	}
}
