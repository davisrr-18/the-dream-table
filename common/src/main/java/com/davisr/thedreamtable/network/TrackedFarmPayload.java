package com.davisr.thedreamtable.network;

import com.davisr.thedreamtable.TheDreamTableMod;
import com.davisr.thedreamtable.farm.FarmAccess;
import com.davisr.thedreamtable.farm.FarmDirectory;
import com.davisr.thedreamtable.farm.FarmLoader;
import com.davisr.thedreamtable.farm.FarmPreferences;
import com.davisr.thedreamtable.platform.Platform;
import com.davisr.thedreamtable.network.codec.RegistryByteBuf;
import com.davisr.thedreamtable.network.codec.PacketCodec;
import com.davisr.thedreamtable.network.codec.CustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * The farm the player tracks, sent when they join so the tracker HUD works before My Farms is ever opened. An empty
 * {@code farmId} means nothing is tracked. After that the My Farms list keeps the client's copy current.
 */
public record TrackedFarmPayload(String farmId, String customName, String dimension, BlockPos where) implements CustomPayload {
	public static final Id<TrackedFarmPayload> ID = new Id<>(new Identifier(TheDreamTableMod.MOD_ID, "tracked_farm"));
	public static final TrackedFarmPayload NONE = new TrackedFarmPayload("", "", "", BlockPos.ORIGIN);
	public static final PacketCodec<RegistryByteBuf, TrackedFarmPayload> CODEC = PacketCodec.of(
			(payload, buf) -> {
				buf.writeString(payload.farmId(), LoadedFarmsPayload.MAX_ID);
				buf.writeString(payload.customName(), 128);
				buf.writeString(payload.dimension(), 256);
				buf.writeBlockPos(payload.where());
			},
			buf -> new TrackedFarmPayload(buf.readString(LoadedFarmsPayload.MAX_ID), buf.readString(128), buf.readString(256), buf.readBlockPos()));

	public boolean isNone() {
		return farmId.isEmpty();
	}

	/** The same display data from a My Farms entry, or {@link #NONE} when no entry is tracked. */
	public static TrackedFarmPayload of(FarmLoader.Entry entry) {
		return entry == null ? NONE : new TrackedFarmPayload(entry.farmId(), entry.customName(), entry.dimension(), entry.where());
	}

	public static void register() {
		Platform.network().registerServerToClient(ID, CODEC);
		Platform.serverEvents().onPlayerJoin((player, server) -> {
			// A client without the mod (or an older one) cannot receive it; sending anyway is refused or ignored.
			if (Platform.network().canSend(player, ID)) {
				Platform.network().send(player, resolve(player, server));
			}
		});
	}

	/** Re-checks the saved choice against the directory and the player's access, as every list entry is. */
	private static TrackedFarmPayload resolve(ServerPlayerEntity player, MinecraftServer server) {
		return FarmPreferences.of(server).tracked(player.getUuid())
				.flatMap(key -> FarmDirectory.resolve(server, key))
				.filter(located -> FarmAccess.isOwner(located.record(), player.getUuid()))
				.map(located -> new TrackedFarmPayload(located.farm().farmId(),
						located.farm().customName() == null ? "" : located.farm().customName(),
						located.key().dimension().toString(), located.farm().where()))
				.orElse(NONE);
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
