package com.davisr.thedreamtable.client;

import com.davisr.thedreamtable.network.FarmAreasPayload;
import com.davisr.thedreamtable.network.FarmAreasPayload.WatchAreasPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * Outlines other players' farm areas near the player when "Show Other Farms" is on in Options: orange while a farm
 * is being built or taken apart, green once finished. The server sends the areas every two seconds while it's on.
 */
public final class OtherFarms {
	private static List<FarmAreasPayload.Area> areas = List.of();

	private OtherFarms() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(FarmAreasPayload.ID, (payload, context) -> areas = payload.areas());
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			areas = List.of();
			if (ClientSettings.showOtherFarms()) {
				tellServer(true);
			}
		});
		WorldRenderEvents.AFTER_ENTITIES.register(OtherFarms::render);
	}

	public static void setShown(boolean shown) {
		ClientSettings.setShowOtherFarms(shown);
		if (!shown) {
			areas = List.of();
		}
		tellServer(shown);
	}

	private static void tellServer(boolean watch) {
		if (ClientPlayNetworking.canSend(WatchAreasPayload.ID)) {
			ClientPlayNetworking.send(new WatchAreasPayload(watch));
		}
	}

	private static void render(WorldRenderContext context) {
		if (!ClientSettings.showOtherFarms() || areas.isEmpty() || context.consumers() == null || context.matrixStack() == null) {
			return;
		}
		Vec3d camera = context.camera().getPos();
		MatrixStack matrices = context.matrixStack();
		VertexConsumer lines = context.consumers().getBuffer(RenderLayer.getLines());
		matrices.push();
		matrices.translate(-camera.x, -camera.y, -camera.z);
		for (FarmAreasPayload.Area area : areas) {
			if (area.finished()) {
				WorldRenderer.drawBox(matrices, lines, area.box(), 0.45F, 0.95F, 0.55F, 0.8F);
			} else {
				WorldRenderer.drawBox(matrices, lines, area.box(), 1.0F, 0.62F, 0.25F, 0.9F);
			}
		}
		matrices.pop();
	}
}
