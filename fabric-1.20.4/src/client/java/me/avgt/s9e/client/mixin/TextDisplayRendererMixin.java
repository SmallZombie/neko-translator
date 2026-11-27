package me.avgt.s9e.client.mixin;

import me.avgt.s9e.client.NekoTranslatorClient;
import net.minecraft.client.renderer.entity.DisplayRenderer;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Replaces the layout a text display draws with the layout of its translation.
 *
 * <p>Only the cached layout of the current text is replaced; the entity's own text is never
 * touched, so nothing is written back and the vanilla cache of the original stays intact for
 * the moment the switch is turned off.
 */
@Mixin(DisplayRenderer.TextDisplayRenderer.class)
public abstract class TextDisplayRendererMixin {
	@Redirect(
			method = "renderInner",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/Display$TextDisplay;cacheDisplay(Lnet/minecraft/world/entity/Display$TextDisplay$LineSplitter;)Lnet/minecraft/world/entity/Display$TextDisplay$CachedInfo;"
			)
	)
	private static Display.TextDisplay.CachedInfo neko$translatedInfo(
			final Display.TextDisplay entity,
			final Display.TextDisplay.LineSplitter splitter) {
		return NekoTranslatorClient.textDisplayInfo(entity, splitter);
	}
}
