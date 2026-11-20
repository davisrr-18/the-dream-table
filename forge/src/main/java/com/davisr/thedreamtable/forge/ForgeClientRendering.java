package com.davisr.thedreamtable.forge;

import com.davisr.thedreamtable.TheDreamTableMod;
import com.davisr.thedreamtable.platform.ClientRendering;
import com.davisr.thedreamtable.platform.RenderFrame;
import com.davisr.thedreamtable.platform.RenderStage;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.gui.screen.ingame.ScreenHandlerProvider;
import net.minecraft.client.render.DefaultFramebufferSet;
import net.minecraft.client.render.FramePass;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.block.entity.state.BlockEntityRenderState;
import net.minecraft.client.render.state.WorldRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;
import net.minecraftforge.client.FramePassManager;
import net.minecraftforge.client.event.AddFramePassEvent;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RenderHighlightEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Forge's world render pass, HUD, screen and renderer registration. Only loaded by the client setup.
 *
 * <p>Forge 61 has no per-stage world render events, only {@link AddFramePassEvent}: a frame pass added after all of
 * the game's own passes, in the frame graph (so the model-view matrix of {@code RenderSystem} still holds the camera
 * rotation, as {@link RenderStage} requires). It draws on the main framebuffer after translucent terrain, particles,
 * clouds and weather. Both stages therefore run in that one pass, {@code AFTER_ENTITIES} listeners first.
 */
final class ForgeClientRendering implements ClientRendering {
	private final BusGroup modBus;
	private final List<Consumer<RenderFrame>> afterEntities = new ArrayList<>();
	private final List<Consumer<RenderFrame>> endMain = new ArrayList<>();
	private boolean passRegistered;
	private int hudLayers;

	ForgeClientRendering(BusGroup modBus) {
		this.modBus = modBus;
	}

	@Override
	public void onWorldRender(RenderStage stage, Consumer<RenderFrame> listener) {
		switch (stage) {
			case AFTER_ENTITIES -> afterEntities.add(listener);
			case END_MAIN -> endMain.add(listener);
		}
		if (!passRegistered) {
			passRegistered = true;
			AddFramePassEvent.BUS.addListener(event -> event.addPass(Identifier.of(TheDreamTableMod.MOD_ID, "world"), new FramePassManager.PassDefinition() {
				@Override
				public void extracts(DefaultFramebufferSet bundle, FramePass pass, RenderTickCounter tickCounter) {
					// Every pass must bind a target; the main one is the only one used when Fabulous graphics is off.
					bundle.mainFramebuffer = pass.transfer(bundle.mainFramebuffer);
				}

				@Override
				public void executes(WorldRenderState state) {
					draw();
				}
			}));
		}
	}

	private void draw() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null) {
			return;
		}
		// An identity stack in camera-relative space, as the world render's own; the rotation is in RenderSystem's matrix.
		MatrixStack matrices = new MatrixStack();
		VertexConsumerProvider.Immediate consumers = client.getBufferBuilders().getEntityVertexConsumers();
		RenderFrame frame = new RenderFrame(client.world, matrices, consumers, client.gameRenderer.getCamera());
		afterEntities.forEach(listener -> listener.accept(frame));
		endMain.forEach(listener -> listener.accept(frame));
		// The game flushed this buffer source before this pass ran; what the listeners wrote would wait for the next frame.
		consumers.draw();
	}

	@Override
	public void hideBlockOutlineWhen(BooleanSupplier hidden) {
		// Returning true cancels the event, which makes Forge draw no outline for the block.
		RenderHighlightEvent.Block.BUS.addListener((RenderHighlightEvent.Block event) -> hidden.getAsBoolean());
	}

	@Override
	public void onHud(Consumer<DrawContext> listener) {
		// A layer added on top of the HUD draws after the vanilla ones, like the other loaders' post-HUD hooks.
		Identifier name = Identifier.of(TheDreamTableMod.MOD_ID, "hud_" + hudLayers++);
		AddGuiOverlayLayersEvent.BUS.addListener(
				event -> event.getLayeredDraw().add(name, (context, tickCounter) -> listener.accept(context)));
	}

	@Override
	public <T extends ScreenHandler, S extends Screen & ScreenHandlerProvider<T>> void registerScreen(Supplier<? extends ScreenHandlerType<? extends T>> type, ScreenFactory<T, S> factory) {
		// The screen table is not thread-safe, and client setup runs off the main thread.
		FMLClientSetupEvent.getBus(modBus).addListener(event -> event.enqueueWork(() -> HandledScreens.register(type.get(), factory::create)));
	}

	@Override
	public <T extends BlockEntity, S extends BlockEntityRenderState> void registerBlockEntityRenderer(Supplier<? extends BlockEntityType<? extends T>> type,
			BlockEntityRendererFactory<T, S> factory) {
		EntityRenderersEvent.RegisterRenderers.BUS.addListener(event -> event.registerBlockEntityRenderer(type.get(), factory));
	}
}
