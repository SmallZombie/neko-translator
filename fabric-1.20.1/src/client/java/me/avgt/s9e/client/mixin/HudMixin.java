package me.avgt.s9e.client.mixin;

import me.avgt.s9e.client.NekoTranslatorClient;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Swaps the item name that vanilla shows above the hotbar for the translated one and adds the
 * `[T]` marker. The name is re-centred because the translation can be wider than the original:
 * vanilla measures the line before it draws it, so the width is what has to change.
 */
@Mixin(Gui.class)
public abstract class HudMixin {
	@Shadow private ItemStack lastToolHighlight;

	@Redirect(
			method = "renderSelectedItemName",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/gui/Font;width(Lnet/minecraft/network/chat/FormattedText;)I"
			)
	)
	private int neko$translatedNameWidth(final Font font, final FormattedText text) {
		return font.width(NekoTranslatorClient.itemName(this.lastToolHighlight, (Component) text));
	}

	@Redirect(
			method = "renderSelectedItemName",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)I"
			)
	)
	private int neko$translatedName(
			final GuiGraphics graphics,
			final Font font,
			final Component text,
			final int x,
			final int y,
			final int color) {
		return graphics.drawString(font, NekoTranslatorClient.itemName(this.lastToolHighlight, text), x, y, color);
	}
}
