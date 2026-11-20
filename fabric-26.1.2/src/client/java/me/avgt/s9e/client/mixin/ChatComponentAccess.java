package me.avgt.s9e.client.mixin;

import java.util.List;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Gives the chat translator access to the stored chat lines. */
@Mixin(ChatComponent.class)
public interface ChatComponentAccess {
	@Accessor("allMessages")
	List<GuiMessage> nekoAllMessages();

	@Invoker("refreshTrimmedMessages")
	void nekoRefresh();
}
