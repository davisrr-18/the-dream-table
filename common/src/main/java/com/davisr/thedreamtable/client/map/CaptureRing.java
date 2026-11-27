package com.davisr.thedreamtable.client.map;

import com.davisr.thedreamtable.client.hologram.GhostRenderer;
import com.davisr.thedreamtable.platform.Platform;
import com.davisr.thedreamtable.platform.RenderFrame;
import com.davisr.thedreamtable.platform.RenderStage;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

/**
 * Draws the table's range ring in the capture map, as the placement map does (the same {@link MapTerrain.Ring} and edge
 * brackets), since the placement hologram that draws it there is switched off in the capture variant.
 */
public final class CaptureRing {
	/** The ring sees the whole reach again about this often, in ticks. */
	private static final int RING_TICKS = 100;

	private static MapTerrain.Ring ring;
	private static long ringTime = Long.MIN_VALUE;

	private CaptureRing() {
	}

	public static void register() {
		Platform.clientRendering().onWorldRender(RenderStage.AFTER_TRANSLUCENT, CaptureRing::render);
	}

	private static void render(RenderFrame context) {
		BlockPos table = MapMode.capture() ? MapMode.tablePos() : null;
		if (table == null || context.world() == null || context.matrixStack() == null) {
			ring = null;
			return;
		}
		long now = context.world().getTime();
		if (ring == null || !ring.isFor(table)) {
			ring = new MapTerrain.Ring(context.world(), table);
		} else if (now != ringTime) {
			// Once per world tick, however many frames are drawn.
			ring.refresh(context.world(), MapTerrain.columnsPerTick(RING_TICKS));
		}
		ringTime = now;
		if (ring.edge().isEmpty()) {
			return;
		}
		Vec3d camera = context.camera().getPos();
		Vector3f view = context.camera().getHorizontalPlane();
		MatrixStack matrices = context.matrixStack();
		matrices.push();
		matrices.translate(-camera.x, -camera.y, -camera.z);
		GhostRenderer.drawEdge(matrices, ring.edge(), camera, new Vec3d(view.x(), view.y(), view.z()));
		matrices.pop();
	}
}
