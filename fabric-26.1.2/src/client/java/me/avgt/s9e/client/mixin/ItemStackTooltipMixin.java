package me.avgt.s9e.client.mixin;

import java.util.List;
import me.avgt.s9e.client.NekoTranslatorClient;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Replaces the tooltip of an item with the decorated one: the `[T]` marker behind the name and,
 * when it is known, the translated name and description lines.
 *
 * <p>The mixin replaces the returned list instead of editing it in place because vanilla returns
 * an immutable list for items that only show the "hidden NBT" warning.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackTooltipMixin {
	@Inject(method = "getTooltipLines", at = @At("RETURN"), cancellable = true)
	private void neko$decorateTooltip(
			final Item.TooltipContext context,
			final Player player,
			final TooltipFlag flag,
			final CallbackInfoReturnable<List<Component>> callbackInfo) {
		List<Component> lines = callbackInfo.getReturnValue();
		List<Component> decorated = NekoTranslatorClient.itemTooltip(lines);
		if (decorated != lines) {
			callbackInfo.setReturnValue(decorated);
		}
	}
}
