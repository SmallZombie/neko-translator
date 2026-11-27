package me.avgt.s9e.client.mixin;

import me.avgt.s9e.client.NekoTranslatorClient;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Swaps the name of every boss bar for its translation. The name is centred on the bar and
 * vanilla measures it first, so the width and the drawn line are both replaced.
 *
 * <p>The frame boundary also tells the key handler which bars are on screen, because a boss bar
 * has no surface the player could target.
 */
@Mixin(BossHealthOverlay.class)
public abstract class BossHealthOverlayMixin {
	@Inject(method = "render", at = @At("HEAD"))
	private void neko$beginBossBarFrame(final GuiGraphics graphics, final CallbackInfo callbackInfo) {
		NekoTranslatorClient.beginBossbarFrame();
	}

	@Redirect(
			method = "render",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/gui/Font;width(Lnet/minecraft/network/chat/FormattedText;)I"
			)
	)
	private int neko$translatedBossBarWidth(final Font font, final FormattedText text) {
		return font.width(NekoTranslatorClient.bossbarText((Component) text));
	}

	@Redirect(
			method = "render",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)I"
			)
	)
	private int neko$translatedBossBarName(
			final GuiGraphics graphics,
			final Font font,
			final Component text,
			final int x,
			final int y,
			final int color) {
		return graphics.drawString(font, NekoTranslatorClient.bossbarText(text), x, y, color);
	}
}
