package me.avgt.s9e.client.mixin;

import me.avgt.s9e.client.debug.NekoDebugEntries;
import net.minecraft.client.gui.components.debug.DebugScreenEntryList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps the console traffic log in sync with the debug screen switch. */
@Mixin(DebugScreenEntryList.class)
public abstract class DebugScreenEntryListMixin {
	@Inject(method = "rebuildCurrentList", at = @At("TAIL"))
	private void neko$syncRequestLogging(final CallbackInfo info) {
		NekoDebugEntries.syncRequestLogging((DebugScreenEntryList) (Object) this);
	}
}
