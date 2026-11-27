package me.avgt.s9e.client.mixin;

import me.avgt.s9e.client.NekoTranslatorClient;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Swaps the title, the subtitle and the action bar message for their translation. The text is
 * drawn relative to the middle of the screen, so it is re-centred after the marker made it wider.
 */
@Mixin(Gui.class)
public abstract class HudTextMixin {
	@Redirect(
			method = "renderTitle",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/gui/GuiGraphics;drawStringWithBackdrop(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIII)I",
					ordinal = 0
			)
	)
	private int neko$translatedTitle(
			final GuiGraphics graphics,
			final Font font,
			final Component text,
			final int x,
			final int y,
			final int width,
			final int color) {
		return neko$draw(graphics, font, text, y, color);
	}

	@Redirect(
			method = "renderTitle",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/gui/GuiGraphics;drawStringWithBackdrop(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIII)I",
					ordinal = 1
			)
	)
	private int neko$translatedSubtitle(
			final GuiGraphics graphics,
			final Font font,
			final Component text,
			final int x,
			final int y,
			final int width,
			final int color) {
		return neko$draw(graphics, font, text, y, color);
	}

	@Redirect(
			method = "renderOverlayMessage",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/gui/GuiGraphics;drawStringWithBackdrop(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIII)I"
			)
	)
	private int neko$translatedActionBar(
			final GuiGraphics graphics,
			final Font font,
			final Component text,
			final int x,
			final int y,
			final int width,
			final int color) {
		return neko$draw(graphics, font, text, y, color);
	}

	private static int neko$draw(
			final GuiGraphics graphics, final Font font, final Component text, final int y, final int color) {
		Component shown = NekoTranslatorClient.hudText(text);
		int shownWidth = font.width(shown);
		return graphics.drawStringWithBackdrop(font, shown, -shownWidth / 2, y, shownWidth, color);
	}
}
