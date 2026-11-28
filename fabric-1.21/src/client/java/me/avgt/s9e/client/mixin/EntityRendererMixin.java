package me.avgt.s9e.client.mixin;

import me.avgt.s9e.client.NekoTranslatorClient;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * Replaces the text of a name tag with its translation.
 *
 * <p>Only the component passed to the renderer of the current frame is replaced; the entity's
 * own name is never touched. The call site is the one in {@code render}, so the score line a
 * player renderer adds below the name is left alone, and renderers that draw the name of their
 * item instead (item frames) are not affected.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
	@ModifyArgs(
			method = "render",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;renderNameTag(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V"
			)
	)
	private void neko$translatedNameTag(final Args args) {
		// Player names are proper nouns and are never translated. The first argument is the entity.
		if (args.get(0) instanceof Player) return;

		Component original = args.get(1);
		args.set(1, NekoTranslatorClient.entityName(original));
	}
}
