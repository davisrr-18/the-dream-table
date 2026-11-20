package com.davisr.thedreamtable.client;

import com.davisr.thedreamtable.network.FarmAreasPayload;
import com.davisr.thedreamtable.network.FarmAreasPayload.WatchAreasPayload;
import com.davisr.thedreamtable.platform.Platform;
import com.davisr.thedreamtable.platform.RenderFrame;
import com.davisr.thedreamtable.platform.RenderStage;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShapes;

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
		Platform.clientNetwork().registerReceiver(FarmAreasPayload.ID, payload -> areas = payload.areas());
		Platform.clientEvents().onJoin(client -> {
			areas = List.of();
			if (ClientSettings.showOtherFarms()) {
				tellServer(true);
			}
		});
		Platform.clientRendering().onWorldRender(RenderStage.AFTER_ENTITIES, OtherFarms::render);
	}

	static void clear() {
		areas = List.of();
	}

	public static void setShown(boolean shown) {
		ClientSettings.setShowOtherFarms(shown);
		if (!shown) {
			areas = List.of();
		}
		tellServer(shown);
	}

	private static void tellServer(boolean watch) {
		if (Platform.clientNetwork().canSend(WatchAreasPayload.ID)) {
			Platform.clientNetwork().send(new WatchAreasPayload(watch));
		}
	}

	private static void render(RenderFrame context) {
		if (!ClientSettings.showOtherFarms() || areas.isEmpty() || context.consumers() == null || context.matrixStack() == null) {
			return;
		}
		Vec3d camera = context.camera().getCameraPos();
		MatrixStack matrices = context.matrixStack();
		VertexConsumer lines = context.consumers().getBuffer(RenderLayers.lines());
		// Lines carry their width per vertex in 1.21.11; the game's own selection outline uses this width.
		float width = MinecraftClient.getInstance().getWindow().getMinimumLineWidth();
		matrices.push();
		matrices.translate(-camera.x, -camera.y, -camera.z);
		for (FarmAreasPayload.Area area : areas) {
			int color = area.finished() ? ColorHelper.fromFloats(0.8F, 0.45F, 0.95F, 0.55F) : ColorHelper.fromFloats(0.9F, 1.0F, 0.62F, 0.25F);
			VertexRendering.drawOutline(matrices, lines, VoxelShapes.cuboid(area.box()), 0.0, 0.0, 0.0, color, width);
		}
		matrices.pop();
	}
}
