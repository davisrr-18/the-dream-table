package com.davisr.thedreamtable.fabric;

import com.davisr.thedreamtable.platform.ClientRendering;
import com.davisr.thedreamtable.platform.RenderFrame;
import com.davisr.thedreamtable.platform.RenderStage;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.gui.screen.ingame.ScreenHandlerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** Fabric API's world render and HUD events. Only loaded by the client entrypoint. */
final class FabricClientRendering implements ClientRendering {
	@Override
	public void onWorldRender(RenderStage stage, Consumer<RenderFrame> listener) {
		switch (stage) {
			case AFTER_ENTITIES -> WorldRenderEvents.AFTER_ENTITIES.register(context -> listener.accept(frame(context)));
			case AFTER_TRANSLUCENT -> WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> listener.accept(frame(context)));
		}
	}

	@Override
	public void onHud(Consumer<DrawContext> listener) {
		HudRenderCallback.EVENT.register((context, tickCounter) -> listener.accept(context));
	}

	@Override
	public <T extends ScreenHandler, S extends Screen & ScreenHandlerProvider<T>> void registerScreen(Supplier<? extends ScreenHandlerType<? extends T>> type, ScreenFactory<T, S> factory) {
		HandledScreens.register(type.get(), factory::create);
	}

	@Override
	public <T extends BlockEntity> void registerBlockEntityRenderer(Supplier<? extends BlockEntityType<? extends T>> type, BlockEntityRendererFactory<T> factory) {
		BlockEntityRendererFactories.register(type.get(), factory);
	}

	private static RenderFrame frame(WorldRenderContext context) {
		return new RenderFrame(context.world(), context.matrixStack(), context.consumers(), context.camera());
	}
}
