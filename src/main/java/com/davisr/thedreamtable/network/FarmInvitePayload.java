package com.davisr.thedreamtable.network;

import com.davisr.thedreamtable.DreamTableConfig;
import com.davisr.thedreamtable.TheDreamTableMod;
import com.davisr.thedreamtable.farm.FarmAction;
import com.davisr.thedreamtable.farm.FarmAreas;
import com.davisr.thedreamtable.farm.FarmRole;
import com.davisr.thedreamtable.farm.FarmSharing;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.UserCache;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/** Owner invitation by a bounded player name; profile lookup is followed by full server-thread revalidation. */
public record FarmInvitePayload(BlockPos table, String key, String playerName, FarmRole role) implements CustomPayload {
	public static final Id<FarmInvitePayload> ID = new Id<>(Identifier.of(TheDreamTableMod.MOD_ID, "farm_invite"));
	public static final PacketCodec<RegistryByteBuf, FarmInvitePayload> CODEC = PacketCodec.of(
			(payload, buf) -> {
				buf.writeBlockPos(payload.table());
				buf.writeString(payload.key(), 256);
				buf.writeString(payload.playerName(), 64);
				buf.writeEnumConstant(payload.role());
			},
			buf -> new FarmInvitePayload(buf.readBlockPos(), buf.readString(256), buf.readString(64), buf.readEnumConstant(FarmRole.class)));

	public static void register() {
		PayloadTypeRegistry.playC2S().register(ID, CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ID, (payload, context) -> context.server().execute(() -> begin(context.player(), payload)));
	}

	private static void begin(ServerPlayerEntity player, FarmInvitePayload payload) {
		if (payload.playerName().isBlank() || payload.role() == FarmRole.OWNER
				|| FarmNetwork.authorize(player, payload.table(), payload.key(), FarmAction.SHARE, FarmActionResultPayload.Action.INVITE) == null) {
			if (payload.playerName().isBlank() || payload.role() == FarmRole.OWNER) {
				FarmNetwork.result(player, payload.key(), FarmActionResultPayload.Action.INVITE, FarmActionResultPayload.Result.INVALID_INPUT);
			}
			return;
		}
		UserCache cache = player.getServer().getUserCache();
		if (cache == null) {
			FarmNetwork.result(player, payload.key(), FarmActionResultPayload.Action.INVITE, FarmActionResultPayload.Result.NOT_FOUND);
			return;
		}
		cache.findByNameAsync(payload.playerName().trim()).thenAccept(profile -> player.getServer().execute(() -> {
			if (profile.isEmpty()) {
				FarmNetwork.result(player, payload.key(), FarmActionResultPayload.Action.INVITE, FarmActionResultPayload.Result.NOT_FOUND);
				return;
			}
			complete(player, payload, profile.orElseThrow());
		}));
	}

	private static void complete(ServerPlayerEntity player, FarmInvitePayload payload, GameProfile profile) {
		FarmNetwork.Checked checked = FarmNetwork.authorize(player, payload.table(), payload.key(), FarmAction.SHARE, FarmActionResultPayload.Action.INVITE);
		if (checked == null) {
			return;
		}
		List<FarmAreas> states = new ArrayList<>();
		player.getServer().getWorlds().forEach(world -> states.add(FarmAreas.of(world)));
		long now = System.currentTimeMillis();
		int pending = FarmSharing.pendingInvites(states, profile.getId(), now);
		FarmSharing.InviteOutcome outcome = FarmSharing.invite(FarmAreas.of(checked.farm().located().world()),
				checked.farm().located().key().instanceId(), player.getUuid(), profile.getId(), profile.getName(), payload.role(), now,
				DreamTableConfig.maxCollaboratorsPerFarm(), DreamTableConfig.maxPendingInvitesPerPlayer(), pending);
		FarmActionResultPayload.Result result = switch (outcome.result()) {
			case SUCCESS -> FarmActionResultPayload.Result.SUCCESS;
			case NOT_FOUND -> FarmActionResultPayload.Result.NOT_FOUND;
			case FORBIDDEN -> FarmActionResultPayload.Result.FORBIDDEN;
			case COLLABORATOR_LIMIT, PENDING_LIMIT -> FarmActionResultPayload.Result.LIMIT_REACHED;
			case SELF, DUPLICATE, INVALID_ROLE -> FarmActionResultPayload.Result.INVALID_INPUT;
		};
		FarmNetwork.result(player, payload.key(), FarmActionResultPayload.Action.INVITE, result);
		FarmNetwork.refresh(player);
		ServerPlayerEntity invited = player.getServer().getPlayerManager().getPlayer(profile.getId());
		if (invited != null) {
			FarmNetwork.refresh(invited);
		}
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
