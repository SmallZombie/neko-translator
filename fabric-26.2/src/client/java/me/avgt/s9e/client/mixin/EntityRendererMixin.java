package me.avgt.s9e.client.mixin;

import me.avgt.s9e.client.NekoTranslatorClient;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Replaces the text of a name tag with its translation.
 *
 * <p>Only the component of the current frame is replaced; the entity's own name is never
 * touched, so nothing is written back and the original stays intact for the moment the switch
 * is turned off.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
	@Inject(method = "getNameTag", at = @At("RETURN"), cancellable = true)
	private void neko$translatedNameTag(final Entity entity, final CallbackInfoReturnable<Component> callback) {
		// Player names are proper nouns and are never translated.
		if (entity instanceof Player) return;

		final Component original = callback.getReturnValue();
		if (original != null) {
			callback.setReturnValue(NekoTranslatorClient.entityName(original));
		}
	}
}
