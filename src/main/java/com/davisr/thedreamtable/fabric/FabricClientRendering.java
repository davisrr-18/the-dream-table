package com.davisr.thedreamtable.fabric;

import com.davisr.thedreamtable.platform.ClientRendering;
import com.davisr.thedreamtable.platform.RenderFrame;
import com.davisr.thedreamtable.platform.RenderStage;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.gui.DrawContext;

import java.util.function.Consumer;

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

	private static RenderFrame frame(WorldRenderContext context) {
		return new RenderFrame(context.world(), context.matrixStack(), context.consumers(), context.camera());
	}
}
