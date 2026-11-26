package com.davisr.thedreamtable.client;

import com.davisr.thedreamtable.block.entity.DreamTableBlockEntity;
import com.davisr.thedreamtable.block.entity.SessionQueue;
import com.davisr.thedreamtable.platform.Platform;
import com.davisr.thedreamtable.platform.RenderFrame;
import com.davisr.thedreamtable.platform.RenderStage;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * Outlines, in blue, where the player's own queued farms will stand, so a farm can be aimed clear of them. It draws while the
 * hologram follows a table (a farm is chosen there, which includes map mode, a view of the same world) and only that player's
 * queue at that table, only in the world the farm is queued in. The server sends the boxes with the session, so nothing here asks for them.
 */
public final class QueuedFarms {
	private static final float RED = 0.3F;
	private static final float GREEN = 0.6F;
	private static final float BLUE = 1.0F;
	private static final float ALPHA = 0.95F;

	private QueuedFarms() {
	}

	public static void register() {
		Platform.clientRendering().onWorldRender(RenderStage.AFTER_ENTITIES, QueuedFarms::render);
	}

	private static void render(RenderFrame context) {
		BlockPos tablePos = DreamTableHologram.trackedTable();
		ClientPlayerEntity player = MinecraftClient.getInstance().player;
		if (tablePos == null || player == null || context.world() == null || !(context.world().getBlockEntity(tablePos) instanceof DreamTableBlockEntity table)) {
			return;
		}
		List<SessionQueue.Area> queued = table.session(player).queuedAreas();
		if (queued.isEmpty()) {
			return;
		}
		Identifier world = context.world().getRegistryKey().getValue();
		AreaOutlines.draw(context, outliner -> {
			for (SessionQueue.Area area : queued) {
				if (area.dimension().equals(world)) {
					outliner.box(area.box(), RED, GREEN, BLUE, ALPHA);
				}
			}
		});
	}
}
