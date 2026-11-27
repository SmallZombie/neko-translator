package me.avgt.s9e.client.mixin;

import me.avgt.s9e.client.lang.LanguageKeys;
import net.minecraft.client.resources.language.LanguageManager;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps the index of keys the current client language defines up to date. */
@Mixin(LanguageManager.class)
public abstract class LanguageManagerMixin {
	@Shadow private String currentCode;

	@Inject(method = "onResourceManagerReload", at = @At("TAIL"))
	private void neko$indexLanguageKeys(final ResourceManager resourceManager, final CallbackInfo callbackInfo) {
		LanguageKeys.INSTANCE.reload(resourceManager, this.currentCode);
	}
}
