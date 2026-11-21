package me.avgt.s9e.client.mixin;

import me.avgt.s9e.client.NekoTranslatorClient;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Swaps the item name that vanilla shows above the hotbar for the translated one and adds the
 * `[T]` marker. The name is re-centred because the translation can be wider than the original.
 */
@Mixin(Hud.class)
public abstract class HudMixin {
	@Shadow private ItemStack lastToolHighlight;

	@Redirect(
			method = "extractSelectedItemName",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;textWithBackdrop(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIII)V"
			)
	)
	private void neko$translatedItemName(
			final GuiGraphicsExtractor graphics,
			final Font font,
			final Component text,
			final int x,
			final int y,
			final int width,
			final int color) {
		Component shown = NekoTranslatorClient.itemName(this.lastToolHighlight, text);
		int shownWidth = font.width(shown);
		graphics.textWithBackdrop(font, shown, (graphics.guiWidth() - shownWidth) / 2, y, shownWidth, color);
	}
}
