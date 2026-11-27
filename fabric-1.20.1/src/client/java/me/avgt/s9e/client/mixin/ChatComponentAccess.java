package me.avgt.s9e.client.mixin;

import java.util.List;

import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Gives the chat translator access to the stored chat lines and the scroll position. */
@Mixin(ChatComponent.class)
public interface ChatComponentAccess {
	@Accessor("allMessages")
	List<GuiMessage> nekoAllMessages();

	@Invoker("refreshTrimmedMessage")
	void nekoRefresh();

	@Accessor("chatScrollbarPos")
	int nekoChatScrollbarPos();

	@Accessor("chatScrollbarPos")
	void nekoSetChatScrollbarPos(int pos);

	@Accessor("newMessageSinceScroll")
	boolean nekoNewMessageSinceScroll();

	@Accessor("newMessageSinceScroll")
	void nekoSetNewMessageSinceScroll(boolean value);
}
