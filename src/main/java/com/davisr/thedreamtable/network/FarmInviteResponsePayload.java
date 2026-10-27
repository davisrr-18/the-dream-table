package com.davisr.thedreamtable.network;

import com.davisr.thedreamtable.DreamTableConfig;
import com.davisr.thedreamtable.TheDreamTableMod;
import com.davisr.thedreamtable.farm.FarmAreas;
import com.davisr.thedreamtable.farm.FarmDirectory;
import com.davisr.thedreamtable.farm.FarmSharing;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

/** Accepts or declines exactly one invitation after resolving it from server-owned state. */
public record FarmInviteResponsePayload(BlockPos table, String key, UUID inviteId, boolean accept) implements CustomPayload {
	public static final Id<FarmInviteResponsePayload> ID = new Id<>(Identifier.of(TheDreamTableMod.MOD_ID, "farm_invite_response"));
	public static final PacketCodec<RegistryByteBuf, FarmInviteResponsePayload> CODEC = PacketCodec.of(
			(payload, buf) -> {
				buf.writeBlockPos(payload.table());
				buf.writeString(payload.key(), 256);
				buf.writeUuid(payload.inviteId());
				buf.writeBoolean(payload.accept());
			},
			buf -> new FarmInviteResponsePayload(buf.readBlockPos(), buf.readString(256), buf.readUuid(), buf.readBoolean()));

	public static void register() {
		PayloadTypeRegistry.playC2S().register(ID, CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ID, (payload, context) -> context.server().execute(() -> handle(context.player(), payload)));
	}

	private static void handle(ServerPlayerEntity player, FarmInviteResponsePayload payload) {
		if (FarmNetwork.table(player, payload.table()) == null || !DreamTableConfig.farmSharingEnabled()) {
			FarmNetwork.result(player, payload.key(), action(payload), FarmActionResultPayload.Result.FORBIDDEN);
			return;
		}
		FarmDirectory.Located located = FarmNetwork.resolve(player, payload.key());
		if (located == null) {
			FarmActionResultPayload.Result result = FarmPayloadGuard.parse(payload.key()).key() == null
					? FarmActionResultPayload.Result.INVALID_INPUT : FarmActionResultPayload.Result.NOT_FOUND;
			FarmNetwork.result(player, payload.key(), action(payload), result);
			return;
		}
		FarmAreas areas = FarmAreas.of(located.world());
		FarmActionResultPayload.Result result;
		if (payload.accept()) {
			FarmSharing.AcceptResult accepted = FarmSharing.accept(areas, located.key().instanceId(), player.getUuid(), payload.inviteId(),
					System.currentTimeMillis(), DreamTableConfig.maxCollaboratorsPerFarm());
			result = switch (accepted) {
				case SUCCESS -> FarmActionResultPayload.Result.SUCCESS;
				case EXPIRED -> FarmActionResultPayload.Result.EXPIRED;
				case COLLABORATOR_LIMIT -> FarmActionResultPayload.Result.LIMIT_REACHED;
				case NOT_FOUND -> FarmActionResultPayload.Result.NOT_FOUND;
				case DUPLICATE -> FarmActionResultPayload.Result.INVALID_INPUT;
			};
		} else {
			result = areas.removeInvite(located.key().instanceId(), payload.inviteId(), player.getUuid())
					? FarmActionResultPayload.Result.SUCCESS : FarmActionResultPayload.Result.NOT_FOUND;
		}
		FarmNetwork.result(player, payload.key(), action(payload), result);
		FarmNetwork.refresh(player);
	}

	private static FarmActionResultPayload.Action action(FarmInviteResponsePayload payload) {
		return payload.accept() ? FarmActionResultPayload.Action.ACCEPT_INVITE : FarmActionResultPayload.Action.DECLINE_INVITE;
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
