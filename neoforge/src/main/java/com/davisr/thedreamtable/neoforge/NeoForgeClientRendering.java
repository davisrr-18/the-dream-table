package com.davisr.thedreamtable.neoforge;

import com.davisr.thedreamtable.platform.ClientRendering;
import com.davisr.thedreamtable.platform.RenderFrame;
import com.davisr.thedreamtable.platform.RenderStage;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.ScreenHandlerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** NeoForge's world render stages, HUD, screen and renderer registration. Only loaded by the client setup. */
final class NeoForgeClientRendering implements ClientRendering {
	private final IEventBus modBus;

	NeoForgeClientRendering(IEventBus modBus) {
		this.modBus = modBus;
	}

	@Override
	public void onWorldRender(RenderStage stage, Consumer<RenderFrame> listener) {
		RenderLevelStageEvent.Stage neoStage = switch (stage) {
			case AFTER_ENTITIES -> RenderLevelStageEvent.Stage.AFTER_ENTITIES;
			case AFTER_TRANSLUCENT -> RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS;
		};
		NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.class, event -> {
			if (event.getStage() == neoStage) {
				listener.accept(frame(event));
			}
		});
	}

	@Override
	public void onHud(Consumer<DrawContext> listener) {
		NeoForge.EVENT_BUS.addListener(RenderGuiEvent.Post.class, event -> listener.accept(event.getGuiGraphics()));
	}

	@Override
	public <T extends ScreenHandler, S extends Screen & ScreenHandlerProvider<T>> void registerScreen(Supplier<? extends ScreenHandlerType<? extends T>> type, ScreenFactory<T, S> factory) {
		modBus.addListener(RegisterMenuScreensEvent.class, event -> event.register(type.get(), factory::create));
	}

	@Override
	public <T extends BlockEntity> void registerBlockEntityRenderer(Supplier<? extends BlockEntityType<? extends T>> type, BlockEntityRendererFactory<T> factory) {
		modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, event -> event.registerBlockEntityRenderer(type.get(), factory));
	}

	/** Same parts Fabric hands out: vanilla's world matrix stack and the entity vertex consumers. */
	private static RenderFrame frame(RenderLevelStageEvent event) {
		MinecraftClient client = MinecraftClient.getInstance();
		return new RenderFrame(client.world, event.getPoseStack(), client.getBufferBuilders().getEntityVertexConsumers(), event.getCamera());
	}
}
