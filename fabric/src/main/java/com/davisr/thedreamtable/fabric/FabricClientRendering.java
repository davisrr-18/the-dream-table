package com.davisr.thedreamtable.fabric;

import com.davisr.thedreamtable.platform.ClientRendering;
import com.davisr.thedreamtable.platform.RenderFrame;
import com.davisr.thedreamtable.platform.RenderStage;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.block.entity.state.BlockEntityRenderState;
import net.minecraft.util.Identifier;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.gui.screen.ingame.ScreenHandlerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Fabric API's world render and HUD events. Only loaded by the client entrypoint. */
final class FabricClientRendering implements ClientRendering {
	@Override
	public void onWorldRender(RenderStage stage, Consumer<RenderFrame> listener) {
		switch (stage) {
			case AFTER_ENTITIES -> WorldRenderEvents.AFTER_ENTITIES.register(context -> listener.accept(frame(context)));
			case END_MAIN -> WorldRenderEvents.END_MAIN.register(context -> listener.accept(frame(context)));
		}
	}

	@Override
	public void hideBlockOutlineWhen(BooleanSupplier hidden) {
		WorldRenderEvents.BEFORE_BLOCK_OUTLINE.register((context, outline) -> !hidden.getAsBoolean());
	}

	private static int hudElements;

	@Override
	public void onHud(Consumer<DrawContext> listener) {
		HudElementRegistry.addLast(Identifier.of("the_dream_table", "hud_" + hudElements++), (context, tickCounter) -> listener.accept(context));
	}

	@Override
	public <T extends ScreenHandler, S extends Screen & ScreenHandlerProvider<T>> void registerScreen(Supplier<? extends ScreenHandlerType<? extends T>> type, ScreenFactory<T, S> factory) {
		HandledScreens.register(type.get(), factory::create);
	}

	@Override
	public <T extends BlockEntity, S extends BlockEntityRenderState> void registerBlockEntityRenderer(Supplier<? extends BlockEntityType<? extends T>> type,
			BlockEntityRendererFactory<T, S> factory) {
		BlockEntityRendererFactories.register(type.get(), factory);
	}

	private static RenderFrame frame(WorldRenderContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		return new RenderFrame(client.world, context.matrices(), context.consumers(), client.gameRenderer.getCamera());
	}
}
