package com.davisr.thedreamtable.client;

import com.davisr.thedreamtable.block.entity.DreamTableBlockEntity;
import com.davisr.thedreamtable.network.RequestSessionPayload;
import com.davisr.thedreamtable.network.SessionSyncPayload;
import com.davisr.thedreamtable.platform.Platform;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

/**
 * Receives the local player's own session from the server. A table's copy on the client starts without one,
 * for example after its chunk loads again, so the table the hologram or clipboard follows asks for it once a second.
 */
public final class OwnSessions {
	private static final int RETRY_TICKS = 20;
	private static int cooldown;

	private OwnSessions() {
	}

	public static void register() {
		Platform.clientNetwork().registerReceiver(SessionSyncPayload.ID, payload -> {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client.world != null && client.player != null && client.world.getBlockEntity(payload.table()) instanceof DreamTableBlockEntity table) {
				table.receiveOwnSession(client.player.getUuid(), payload.session());
				BuildProgressLine.observe(payload.table(), table.session(client.player));
			}
		});
		Platform.clientEvents().onTickEnd(OwnSessions::requestMissing);
	}

	private static void requestMissing(MinecraftClient client) {
		if (client.world == null || client.player == null || cooldown-- > 0) {
			return;
		}
		cooldown = RETRY_TICKS;
		request(client, DreamTableHologram.trackedTable());
		request(client, DreamTableClipboard.pinnedTable());
		request(client, BuildProgressLine.watched());
	}

	private static void request(MinecraftClient client, BlockPos pos) {
		if (pos != null && client.world.getBlockEntity(pos) instanceof DreamTableBlockEntity table && !table.hasOwnSession()
				&& Platform.clientNetwork().canSend(RequestSessionPayload.ID)) {
			Platform.clientNetwork().send(new RequestSessionPayload(pos));
		}
	}
}
