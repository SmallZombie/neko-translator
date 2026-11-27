package me.avgt.s9e.client.mixin;

import me.avgt.s9e.client.NekoTranslatorClient;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Swaps the title, the subtitle and the action bar message for their translation. The text is
 * drawn relative to the middle of the screen and vanilla measures it first, so both the width
 * and the drawn line are replaced: that re-centres the text after the marker made it wider.
 */
@Mixin(Gui.class)
public abstract class HudTextMixin {
	@Redirect(
			method = "render",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/gui/Font;width(Lnet/minecraft/network/chat/FormattedText;)I"
			)
	)
	private int neko$translatedWidth(final Font font, final FormattedText text) {
		return font.width(NekoTranslatorClient.hudText((Component) text));
	}

	@Redirect(
			method = "render",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)I"
			)
	)
	private int neko$translatedText(
			final GuiGraphics graphics,
			final Font font,
			final Component text,
			final int x,
			final int y,
			final int color) {
		return graphics.drawString(font, NekoTranslatorClient.hudText(text), x, y, color);
	}
}
