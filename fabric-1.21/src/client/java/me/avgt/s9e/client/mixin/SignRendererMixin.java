package me.avgt.s9e.client.mixin;

import java.util.function.Function;
import me.avgt.s9e.client.NekoTranslatorClient;
import net.minecraft.client.renderer.blockentity.SignRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.block.entity.SignText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Replaces the lines a sign draws with their translation and hangs the `[T]` behind the last
 * line. The marker is drawn as part of that line, so it gets the sign's light, colour and
 * outline for free.
 *
 * <p>Both sides of a sign go through this call; each side is its own unit and is decorated on
 * its own state. Hanging signs extend this renderer, so they are covered as well.
 */
@Mixin(SignRenderer.class)
public abstract class SignRendererMixin {
	@Redirect(
			method = "renderSignText",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/level/block/entity/SignText;getRenderMessages(ZLjava/util/function/Function;)[Lnet/minecraft/util/FormattedCharSequence;"
			)
	)
	private FormattedCharSequence[] neko$decorateSignText(
			final SignText signText,
			final boolean filtered,
			final Function<Component, FormattedCharSequence> prepare) {
		FormattedCharSequence[] original = signText.getRenderMessages(filtered, prepare);
		return NekoTranslatorClient.signLines(signText, filtered, original, prepare);
	}
}
