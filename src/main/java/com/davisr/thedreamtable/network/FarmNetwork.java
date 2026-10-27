package com.davisr.thedreamtable.network;

import com.davisr.thedreamtable.DreamTableConfig;
import com.davisr.thedreamtable.block.entity.DreamTableBlockEntity;
import com.davisr.thedreamtable.farm.FarmAction;
import com.davisr.thedreamtable.farm.FarmDirectory;
import com.davisr.thedreamtable.farm.FarmKey;
import com.davisr.thedreamtable.screen.DreamTableScreenHandler;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/** Shared table, key, directory, and role checks for cooperative payload handlers. */
final class FarmNetwork {
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
		return new Checked(table, new FarmDirectory.Authorized(located, authorization.role()));
	}

	static void result(ServerPlayerEntity player, String key, FarmActionResultPayload.Action action, FarmActionResultPayload.Result result) {
		ServerPlayNetworking.send(player, new FarmActionResultPayload(key, action, result));
	}

	static void refresh(ServerPlayerEntity player) {
		LoadedFarmsPayload.send(player);
	}
}
