package me.avgt.s9e.client.mixin;

import net.minecraft.client.gui.components.events.GuiEventListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets other mixins add widgets to a screen from outside the package. */
@Mixin(net.minecraft.client.gui.screens.Screen.class)
public interface ScreenAccess {
	@Invoker("addRenderableWidget")
	GuiEventListener nekoAddRenderableWidget(GuiEventListener widget);
}
