package com.davisr.thedreamtable.client;

import com.davisr.thedreamtable.platform.RenderFrame;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShapes;

import java.util.function.Consumer;

/** Draws farm areas as box outlines in the world: the one place that sets up the camera-relative line drawing, shared by {@link OtherFarms} and {@link QueuedFarms}. */
final class AreaOutlines {
	private AreaOutlines() {
	}

	/** One box outline in a colour; each component is 0 to 1. */
	interface Outliner {
		void box(Box box, float red, float green, float blue, float alpha);
	}

	/** Runs {@code boxes} with an outliner that draws into this frame; draws nothing when the loader gave the frame no buffers. */
	static void draw(RenderFrame context, Consumer<Outliner> boxes) {
		if (context.consumers() == null || context.matrixStack() == null) {
			return;
		}
		Vec3d camera = context.camera().getCameraPos();
		MatrixStack matrices = context.matrixStack();
		VertexConsumer lines = context.consumers().getBuffer(RenderLayers.lines());
		// Lines carry their width per vertex in 1.21.11; the game's own selection outline uses this width.
		float width = MinecraftClient.getInstance().getWindow().getMinimumLineWidth();
		matrices.push();
		matrices.translate(-camera.x, -camera.y, -camera.z);
		boxes.accept((box, red, green, blue, alpha) ->
				VertexRendering.drawOutline(matrices, lines, VoxelShapes.cuboid(box), 0.0, 0.0, 0.0, ColorHelper.fromFloats(alpha, red, green, blue), width));
		matrices.pop();
	}
}
