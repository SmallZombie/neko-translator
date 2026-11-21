package me.avgt.s9e.client.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read access to the slot the mouse currently points at. */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccess {
	@Accessor("hoveredSlot")
	Slot nekoHoveredSlot();
}
