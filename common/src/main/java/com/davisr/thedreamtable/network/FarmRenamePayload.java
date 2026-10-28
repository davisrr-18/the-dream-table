package com.davisr.thedreamtable.network;

import com.davisr.thedreamtable.TheDreamTableMod;
import com.davisr.thedreamtable.farm.FarmAction;
import com.davisr.thedreamtable.farm.FarmAreas;
import com.davisr.thedreamtable.farm.FarmSharing;
import com.davisr.thedreamtable.platform.Platform;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** Owner-only custom-name mutation with bounded input and server-side authorization. */
public record FarmRenamePayload(BlockPos table, String key, String name) implements CustomPayload {
	public static final Id<FarmRenamePayload> ID = new Id<>(Identifier.of(TheDreamTableMod.MOD_ID, "farm_rename"));
	public static final PacketCodec<RegistryByteBuf, FarmRenamePayload> CODEC = PacketCodec.of(
			(payload, buf) -> {
				buf.writeBlockPos(payload.table());
				buf.writeString(payload.key(), 256);
				buf.writeString(payload.name(), 128);
			},
			buf -> new FarmRenamePayload(buf.readBlockPos(), buf.readString(256), buf.readString(128)));

	public static void register() {
		Platform.network().registerClientToServer(ID, CODEC, (payload, player) -> handle(player, payload));
	}

	private static void handle(ServerPlayerEntity player, FarmRenamePayload payload) {
		FarmNetwork.Checked checked = FarmNetwork.authorize(player, payload.table(), payload.key(), FarmAction.RENAME, FarmActionResultPayload.Action.RENAME);
		if (checked == null) {
			return;
		}
		FarmSharing.RenameResult renamed = FarmSharing.rename(FarmAreas.of(checked.farm().located().world()),
				checked.farm().located().key().instanceId(), player.getUuid(), payload.name());
		FarmActionResultPayload.Result result = switch (renamed) {
			case SUCCESS -> FarmActionResultPayload.Result.SUCCESS;
			case NOT_FOUND -> FarmActionResultPayload.Result.NOT_FOUND;
			case FORBIDDEN -> FarmActionResultPayload.Result.FORBIDDEN;
			case INVALID_INPUT -> FarmActionResultPayload.Result.INVALID_INPUT;
		};
		FarmNetwork.result(player, payload.key(), FarmActionResultPayload.Action.RENAME, result);
		FarmNetwork.refresh(player);
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
