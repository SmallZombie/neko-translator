package me.avgt.s9e.client.mixin;

import me.avgt.s9e.client.NekoTranslatorClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the `[T]` button next to the "Done" button of the book screen and swaps the current
 * page between the original text and its translation.
 *
 * <p>The square button is a normal {@link Button} without a label; the coloured `T` is drawn
 * on top of it so its colour can follow the translation state (grey / yellow / green / red).
 */
@Mixin(BookViewScreen.class)
public abstract class BookViewScreenMixin {
	@Unique private static final int NEKO_BUTTON_SIZE = 20;
	@Unique private static final int NEKO_BUTTON_GAP = 4;
	@Unique private static final int NEKO_DONE_WIDTH = 200;

	@Shadow private int cachedPage;
	@Shadow private int currentPage;
	@Shadow private BookViewScreen.BookAccess bookAccess;

	@Shadow protected abstract int menuControlsTop();

	@Unique private int neko$seenVersion = -1;

	@Inject(method = "init", at = @At("TAIL"))
	private void neko$addTranslateButton(final CallbackInfo callbackInfo) {
		if (!NekoTranslatorClient.bookVisible()) return;

		BookViewScreen screen = (BookViewScreen) (Object) this;
		Button button = Button.builder(Component.empty(), clicked -> NekoTranslatorClient.onBookButton(screen))
				.bounds(neko$buttonX(screen.width), this.menuControlsTop(), NEKO_BUTTON_SIZE, NEKO_BUTTON_SIZE)
				.build();

		((ScreenAccess) this).nekoAddRenderableWidget(button);
	}

	@Inject(method = "extractRenderState", at = @At("HEAD"))
	private void neko$pageFrame(
			final GuiGraphicsExtractor graphics,
			final int mouseX,
			final int mouseY,
			final float partialTick,
			final CallbackInfo callbackInfo) {
		BookViewScreen screen = (BookViewScreen) (Object) this;
		NekoTranslatorClient.onBookFrame(screen, this.currentPage, this.bookAccess.getPage(this.currentPage));
	}

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void neko$drawMarker(
			final GuiGraphicsExtractor graphics,
			final int mouseX,
			final int mouseY,
			final float partialTick,
			final CallbackInfo callbackInfo) {
		BookViewScreen screen = (BookViewScreen) (Object) this;
		if (!NekoTranslatorClient.bookVisible()) return;

		int x = neko$buttonX(screen.width) + 7;
		int y = this.menuControlsTop() + 6;
		graphics.text(Minecraft.getInstance().font, Component.literal("T"), x, y, NekoTranslatorClient.bookColor(screen));
	}

	/** Forces vanilla to re-split the page after the translation state changed. */
	@Inject(method = "visitText", at = @At("HEAD"))
	private void neko$invalidatePageCache(final ActiveTextCollector collector, final boolean clickableOnly, final CallbackInfo callbackInfo) {
		int version = NekoTranslatorClient.bookVersion((BookViewScreen) (Object) this);
		if (version != this.neko$seenVersion) {
			this.neko$seenVersion = version;
			this.cachedPage = -1;
		}
	}

	@Redirect(
			method = "visitText",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/gui/screens/inventory/BookViewScreen$BookAccess;getPage(I)Lnet/minecraft/network/chat/Component;"
			)
	)
	private Component neko$translatedPage(final BookViewScreen.BookAccess access, final int page) {
		return NekoTranslatorClient.bookPage((BookViewScreen) (Object) this, access.getPage(page));
	}

	@Unique
	private static int neko$buttonX(final int screenWidth) {
		return (screenWidth - NEKO_DONE_WIDTH) / 2 - NEKO_BUTTON_GAP - NEKO_BUTTON_SIZE;
	}
}
