package me.avgt.s9e.client.mixin;

import java.util.Map;
import me.avgt.s9e.client.debug.NekoDebugEntries;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the mod's entries to the debug screen registry, which vanilla keeps private. */
@Mixin(DebugScreenEntries.class)
public abstract class DebugScreenEntriesMixin {
	@Shadow private static Map<Identifier, DebugScreenEntry> ENTRIES_BY_ID;

	@Inject(method = "<clinit>", at = @At("TAIL"))
	private static void neko$registerEntries(final CallbackInfo info) {
		NekoDebugEntries.registerEntries(ENTRIES_BY_ID);
	}
}
