package com.davisr.thedreamtable.network;

import com.davisr.thedreamtable.TheDreamTableMod;
import com.davisr.thedreamtable.farm.FarmAction;
import com.davisr.thedreamtable.farm.FarmAreas;
import com.davisr.thedreamtable.farm.FarmPreferences;
import com.davisr.thedreamtable.platform.Platform;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

/** Owner-only collaborator revocation. Role changes use a new invitation after revocation. */
public record FarmCollaboratorPayload(BlockPos table, String key, UUID collaboratorUuid) implements CustomPayload {
	public static final Id<FarmCollaboratorPayload> ID = new Id<>(Identifier.of(TheDreamTableMod.MOD_ID, "farm_collaborator"));
	public static final PacketCodec<RegistryByteBuf, FarmCollaboratorPayload> CODEC = PacketCodec.of(
			(payload, buf) -> {
				buf.writeBlockPos(payload.table());
				buf.writeString(payload.key(), 256);
				buf.writeUuid(payload.collaboratorUuid());
			},
			buf -> new FarmCollaboratorPayload(buf.readBlockPos(), buf.readString(256), buf.readUuid()));

	public static void register() {
		Platform.network().registerClientToServer(ID, CODEC, (payload, player) -> handle(player, payload));
	}

	private static void handle(ServerPlayerEntity player, FarmCollaboratorPayload payload) {
		FarmNetwork.Checked checked = FarmNetwork.authorize(player, payload.table(), payload.key(), FarmAction.SHARE,
				FarmActionResultPayload.Action.REMOVE_COLLABORATOR);
		if (checked == null) {
			return;
		}
		FarmAreas areas = FarmAreas.of(checked.farm().located().world());
		boolean removed = areas.removeGrant(checked.farm().located().key().instanceId(), payload.collaboratorUuid());
		if (removed) {
			FarmPreferences.of(player.getServer()).removeAccess(payload.collaboratorUuid(), checked.farm().located().key());
		}
		FarmNetwork.result(player, payload.key(), FarmActionResultPayload.Action.REMOVE_COLLABORATOR,
				removed ? FarmActionResultPayload.Result.SUCCESS : FarmActionResultPayload.Result.NOT_FOUND);
		FarmNetwork.refresh(player);
		ServerPlayerEntity collaborator = player.getServer().getPlayerManager().getPlayer(payload.collaboratorUuid());
		if (collaborator != null) {
			FarmNetwork.refresh(collaborator);
		}
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
