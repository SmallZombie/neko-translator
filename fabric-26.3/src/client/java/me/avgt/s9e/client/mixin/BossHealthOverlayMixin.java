package me.avgt.s9e.client.mixin;

import me.avgt.s9e.client.NekoTranslatorClient;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Swaps the name of every boss bar for its translation. The name is centred on the bar, so it is
 * re-centred after the marker made it wider.
 *
 * <p>The frame boundary also tells the key handler which bars are on screen, because a boss bar
 * has no surface the player could target.
 */
@Mixin(BossHealthOverlay.class)
public abstract class BossHealthOverlayMixin {
	@Inject(method = "extractRenderState", at = @At("HEAD"))
	private void neko$beginBossBarFrame(final CallbackInfo callbackInfo) {
		NekoTranslatorClient.beginBossbarFrame();
	}

	@Redirect(
			method = "extractRenderState",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"
			)
	)
	private void neko$translatedBossBarName(
			final GuiGraphicsExtractor graphics,
			final Font font,
			final Component text,
			final int x,
			final int y,
			final int color) {
		Component shown = NekoTranslatorClient.bossbarText(text);
		int shownWidth = font.width(shown);
		graphics.text(font, shown, (graphics.guiWidth() - shownWidth) / 2, y, color);
	}
}
