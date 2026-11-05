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
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RenderHighlightEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.davisr.thedreamtable.forge.ForgePlatform.listen;

/** Forge's world render stages, HUD, screen and renderer registration. Only loaded by the client setup. */
final class ForgeClientRendering implements ClientRendering {
	private static final IEventBus BUS = MinecraftForge.EVENT_BUS;

	private final IEventBus modBus;
	private int hudLayers;

	ForgeClientRendering(IEventBus modBus) {
		this.modBus = modBus;
	}

	@Override
	public void onWorldRender(RenderStage stage, Consumer<RenderFrame> listener) {
		RenderLevelStageEvent.Stage forgeStage = switch (stage) {
			case AFTER_ENTITIES -> RenderLevelStageEvent.Stage.AFTER_ENTITIES;
			case AFTER_TRANSLUCENT -> RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS;
		};
		listen(BUS, RenderLevelStageEvent.class, event -> {
			if (event.getStage() == forgeStage) {
				listener.accept(frame(event));
			}
		});
	}

	@Override
	public void hideBlockOutlineWhen(BooleanSupplier hidden) {
		listen(BUS, RenderHighlightEvent.Block.class, event -> {
			if (hidden.getAsBoolean()) {
				event.setCanceled(true);
			}
		});
	}

	@Override
	public void onHud(Consumer<DrawContext> listener) {
		// A layer added on top of the HUD draws after the vanilla ones, like the other loaders' post-HUD hooks.
		Identifier name = new Identifier(TheDreamTableMod.MOD_ID, "hud_" + hudLayers++);
		listen(modBus, AddGuiOverlayLayersEvent.class,
				event -> event.getLayeredDraw().add(name, (context, tickCounter) -> listener.accept(context)));
	}

	@Override
	public <T extends ScreenHandler, S extends Screen & ScreenHandlerProvider<T>> void registerScreen(Supplier<? extends ScreenHandlerType<? extends T>> type, ScreenFactory<T, S> factory) {
		// The screen table is not thread-safe, and client setup runs off the main thread.
		listen(modBus, FMLClientSetupEvent.class, event -> event.enqueueWork(() -> HandledScreens.register(type.get(), factory::create)));
	}

	@Override
	public <T extends BlockEntity> void registerBlockEntityRenderer(Supplier<? extends BlockEntityType<? extends T>> type, BlockEntityRendererFactory<T> factory) {
		listen(modBus, EntityRenderersEvent.RegisterRenderers.class, event -> event.registerBlockEntityRenderer(type.get(), factory));
	}

	/**
	 * Same parts Fabric hands out: the world's view rotation as a matrix stack and the entity vertex consumers. Forge
	 * gives the rotation as a bare matrix, so it is wrapped in a fresh stack. Its getter is deprecated but still the
	 * one Forge 52 offers for it.
	 */
	@SuppressWarnings("removal")
	private static RenderFrame frame(RenderLevelStageEvent event) {
		MinecraftClient client = MinecraftClient.getInstance();
		MatrixStack matrices = new MatrixStack();
		matrices.multiplyPositionMatrix(event.getPoseStack());
		return new RenderFrame(client.world, matrices, client.getBufferBuilders().getEntityVertexConsumers(), event.getCamera());
	}
}
