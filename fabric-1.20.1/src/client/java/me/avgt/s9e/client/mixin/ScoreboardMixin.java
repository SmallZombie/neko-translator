package me.avgt.s9e.client.mixin;

import me.avgt.s9e.client.NekoTranslatorClient;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.scores.Objective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces the sidebar scoreboard with its translation. The panel is laid out from its own text,
 * so while the switch is on the mod draws the whole sidebar itself.
 */
@Mixin(Gui.class)
public abstract class ScoreboardMixin {
	@Inject(method = "displayScoreboardSidebar", at = @At("HEAD"), cancellable = true)
	private void neko$translatedSidebar(
			final GuiGraphics graphics, final Objective objective, final CallbackInfo info) {
		if (NekoTranslatorClient.scoreboard(graphics, objective)) info.cancel();
	}
}
