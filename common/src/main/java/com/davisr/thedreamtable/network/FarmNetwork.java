package com.davisr.thedreamtable.network;

import com.davisr.thedreamtable.DreamTableConfig;
import com.davisr.thedreamtable.block.entity.DreamTableBlockEntity;
import com.davisr.thedreamtable.farm.FarmAction;
import com.davisr.thedreamtable.farm.FarmDirectory;
import com.davisr.thedreamtable.farm.FarmKey;
import com.davisr.thedreamtable.platform.Platform;
import com.davisr.thedreamtable.screen.DreamTableScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Shared table, key, directory, and role checks for cooperative payload handlers. */
final class FarmNetwork {
	private static final RefreshCoalescer REFRESHES = new RefreshCoalescer();

	private FarmNetwork() {
	}

	record Checked(DreamTableBlockEntity table, FarmDirectory.Authorized farm) {
	}

	static @Nullable DreamTableBlockEntity table(ServerPlayerEntity player, BlockPos pos) {
		if (!(player.currentScreenHandler instanceof DreamTableScreenHandler handler)
				|| !handler.getTablePos().equals(pos) || !handler.canUse(player)) {
			return null;
		}
		return player.getWorld().getBlockEntity(pos) instanceof DreamTableBlockEntity table ? table : null;
	}

	static @Nullable FarmDirectory.Located resolve(ServerPlayerEntity player, String externalKey) {
		FarmPayloadGuard.Parsed parsed = FarmPayloadGuard.parse(externalKey);
		return parsed.key() == null ? null : FarmDirectory.resolve(player.getServer(), parsed.key()).orElse(null);
	}

	static @Nullable Checked authorize(ServerPlayerEntity player, BlockPos tablePos, String externalKey, FarmAction action,
			FarmActionResultPayload.Action resultAction) {
		DreamTableBlockEntity table = table(player, tablePos);
		if (table == null) {
			result(player, externalKey, resultAction, FarmActionResultPayload.Result.FORBIDDEN);
			return null;
		}
		FarmDirectory.Authorized farm = authorizeFarm(player, externalKey, action, resultAction);
		return farm == null ? null : new Checked(table, farm);
	}

	/** The key, directory and role checks without an open table, for work that finishes after the player left the screen. */
	static @Nullable FarmDirectory.Authorized authorizeFarm(ServerPlayerEntity player, String externalKey, FarmAction action,
			FarmActionResultPayload.Action resultAction) {
		FarmPayloadGuard.Parsed parsed = FarmPayloadGuard.parse(externalKey);
		if (parsed.key() == null) {
			result(player, externalKey, resultAction, FarmActionResultPayload.Result.INVALID_INPUT);
			return null;
		}
		FarmDirectory.Located located = FarmDirectory.resolve(player.getServer(), parsed.key()).orElse(null);
		FarmPayloadGuard.Authorization authorization = FarmPayloadGuard.authorize(
				located == null ? null : located.record(), player.getUuid(), action, DreamTableConfig.farmSharingEnabled());
		if (authorization.result() != FarmActionResultPayload.Result.SUCCESS) {
			result(player, externalKey, resultAction, authorization.result());
			return null;
		}
		return new FarmDirectory.Authorized(located, authorization.role());
	}

	static void result(ServerPlayerEntity player, String key, FarmActionResultPayload.Action action, FarmActionResultPayload.Result result) {
		Platform.network().send(player, new FarmActionResultPayload(key, action, result));
	}

	/**
	 * Sends the player's My Farms list now, or merges the request into one later send when the last one went out less
	 * than {@link RefreshCoalescer#DEFAULT_INTERVAL_TICKS} ago. Every handler that changes a farm calls this, including
	 * for a third party, so no sequence of requests can force more than a few full scans a second for one player.
	 */
	static void refresh(ServerPlayerEntity player) {
		if (REFRESHES.request(player.getUuid(), player.getServer().getTicks())) {
			LoadedFarmsPayload.send(player);
		}
	}

	/** Releases the merged refreshes once due and forgets leaving players; called once from {@code LoadedFarmsPayload.register}. */
	static void register() {
		Platform.serverEvents().onTickEnd(server -> {
			for (UUID id : REFRESHES.flush(server.getTicks())) {
				ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
				if (player != null) {
					LoadedFarmsPayload.send(player);
				}
			}
		});
		Platform.serverEvents().onPlayerDisconnect((player, server) -> server.execute(() -> REFRESHES.forget(player.getUuid())));
	}
}
