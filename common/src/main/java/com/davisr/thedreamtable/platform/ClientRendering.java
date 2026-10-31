package com.davisr.thedreamtable.platform;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.ScreenHandlerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.text.Text;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Drawing hooks. Installed only on a client; listeners run in registration order. */
public interface ClientRendering {
	/** Draws in the world at the given stage of every frame. */
	void onWorldRender(RenderStage stage, Consumer<RenderFrame> listener);

	/** Skips the game's own selection outline of the looked-at block in every frame in which the supplier says so. */
	void hideBlockOutlineWhen(BooleanSupplier hidden);

	/** Draws on the in-game HUD every frame. */
	void onHud(Consumer<DrawContext> listener);

	/** Opens the screen for menus of the type when the server opens one. The type is read once the loader asks for screens. */
	<T extends ScreenHandler, S extends Screen & ScreenHandlerProvider<T>> void registerScreen(Supplier<? extends ScreenHandlerType<? extends T>> type, ScreenFactory<T, S> factory);

	/** Draws block entities of the type with renderers the factory creates. The type is read once the loader asks for renderers. */
	<T extends BlockEntity> void registerBlockEntityRenderer(Supplier<? extends BlockEntityType<? extends T>> type, BlockEntityRendererFactory<T> factory);

	@FunctionalInterface
	interface ScreenFactory<T extends ScreenHandler, S extends Screen & ScreenHandlerProvider<T>> {
		S create(T handler, PlayerInventory inventory, Text title);
	}
}
