package com.davisr.thedreamtable.client;

import com.davisr.thedreamtable.platform.RenderFrame;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

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
		Vec3d camera = context.camera().getPos();
		MatrixStack matrices = context.matrixStack();
		VertexConsumer lines = context.consumers().getBuffer(RenderLayer.getLines());
		matrices.push();
		matrices.translate(-camera.x, -camera.y, -camera.z);
		boxes.accept((box, red, green, blue, alpha) -> WorldRenderer.drawBox(matrices, lines, box, red, green, blue, alpha));
		matrices.pop();
	}
}
