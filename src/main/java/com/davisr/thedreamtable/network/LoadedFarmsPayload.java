package com.davisr.thedreamtable.network;

import com.davisr.thedreamtable.DreamTableConfig;
import com.davisr.thedreamtable.TheDreamTableMod;
import com.davisr.thedreamtable.farm.FarmLoader;
import com.davisr.thedreamtable.farm.FarmAction;
import com.davisr.thedreamtable.farm.FarmAreas;
import com.davisr.thedreamtable.farm.FarmInvite;
import com.davisr.thedreamtable.farm.FarmKey;
import com.davisr.thedreamtable.farm.FarmRole;
import com.davisr.thedreamtable.platform.Platform;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The player's finished farms for the My Farms pages, and the server's limit. Sent only to that player. */
public record LoadedFarmsPayload(List<FarmLoader.Entry> farms, List<InviteEntry> invites, int limit) implements CustomPayload {
	public static final Id<LoadedFarmsPayload> ID = new Id<>(Identifier.of(TheDreamTableMod.MOD_ID, "loaded_farms"));
	private static final int MAX_FARMS = 512;
	private static final int MAX_INVITES = 512;
	/** Farm and layout ids embed imported file names; the limit is far above any real one and encodes the same below 256. */
	static final int MAX_ID = 1024;
	private static final RequestThrottle THROTTLE = new RequestThrottle();
	public record InviteEntry(String key, UUID inviteId, String farmName, FarmRole role, long expiresAt) {
	}
	private static final PacketCodec<RegistryByteBuf, FarmLoader.Entry> ENTRY = PacketCodec.of(
			(entry, buf) -> {
				buf.writeString(entry.key(), 256);
				buf.writeString(entry.farmId(), MAX_ID);
				buf.writeString(entry.layoutId(), MAX_ID);
				buf.writeString(entry.customName(), 128);
				buf.writeString(entry.dimension(), 256);
				buf.writeBlockPos(entry.where());
				buf.writeEnumConstant(entry.role());
				buf.writeBoolean(entry.favorite());
				buf.writeBoolean(entry.tracked());
				buf.writeBoolean(entry.loaded());
				buf.writeBoolean(entry.ownerQuotaFull());
				buf.writeVarInt(entry.status());
				buf.writeVarInt(entry.missing() + 1);
				buf.writeBoolean(entry.available());
				buf.writeVarInt(entry.storage() + 1);
			},
			buf -> new FarmLoader.Entry(buf.readString(256), buf.readString(MAX_ID), buf.readString(MAX_ID), buf.readString(128), buf.readString(256), buf.readBlockPos(),
					buf.readEnumConstant(com.davisr.thedreamtable.farm.FarmRole.class), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
					buf.readVarInt(), buf.readVarInt() - 1, buf.readBoolean(), buf.readVarInt() - 1));
	private static final PacketCodec<RegistryByteBuf, InviteEntry> INVITE = PacketCodec.of(
			(invite, buf) -> {
				buf.writeString(invite.key(), 256);
				buf.writeUuid(invite.inviteId());
				buf.writeString(invite.farmName(), MAX_ID);
				buf.writeEnumConstant(invite.role());
				buf.writeLong(invite.expiresAt());
			},
			buf -> new InviteEntry(buf.readString(256), buf.readUuid(), buf.readString(MAX_ID), buf.readEnumConstant(FarmRole.class), buf.readLong()));
	public static final PacketCodec<RegistryByteBuf, LoadedFarmsPayload> CODEC = PacketCodec.tuple(
			ENTRY.collect(PacketCodecs.toList(MAX_FARMS)), LoadedFarmsPayload::farms,
			INVITE.collect(PacketCodecs.toList(MAX_INVITES)), LoadedFarmsPayload::invites,
			PacketCodecs.VAR_INT, LoadedFarmsPayload::limit,
			LoadedFarmsPayload::new
	);

	public static void register() {
		Platform.network().registerServerToClient(ID, CODEC);
		THROTTLE.forgetOnDisconnect();
		Platform.network().registerClientToServer(Request.ID, Request.CODEC, (payload, player) -> {
			// A toggle changes state and gets a reply the client waits for, so only plain list reads are budgeted.
			if (payload.toggle().isEmpty() && !THROTTLE.allow(player.getUuid(), player.getServer().getTicks())) {
				return;
			}
			FarmLoader loader = FarmLoader.of(player.getServer());
			if (FarmNetwork.table(player, payload.table()) == null) {
				return;
			}
			if (!payload.toggle().isEmpty()) {
				FarmNetwork.Checked checked = FarmNetwork.authorize(player, payload.table(), payload.toggle(), FarmAction.KEEP_LOADED,
						FarmActionResultPayload.Action.KEEP_LOADED);
				if (checked != null) {
					FarmLoader.ToggleResult result = loader.toggle(player.getServer(), player.getUuid(), checked.farm().located().key());
					FarmActionResultPayload.Result response = switch (result) {
						case SELECTED, UNSELECTED -> FarmActionResultPayload.Result.SUCCESS;
						case OWNER_QUOTA_FULL -> FarmActionResultPayload.Result.LIMIT_REACHED;
						case NOT_FOUND -> FarmActionResultPayload.Result.NOT_FOUND;
						case FORBIDDEN -> FarmActionResultPayload.Result.FORBIDDEN;
					};
					FarmNetwork.result(player, payload.toggle(), FarmActionResultPayload.Action.KEEP_LOADED, response);
					if (result == FarmLoader.ToggleResult.OWNER_QUOTA_FULL) {
						player.sendMessage(Text.translatable("gui.the_dream_table.loaded_full", DreamTableConfig.maxLoadedFarms()), true);
					}
				}
			}
			send(player);
		});
	}

	public static void send(ServerPlayerEntity player) {
		List<FarmLoader.Entry> farms = FarmLoader.of(player.getServer()).farmsOf(player.getServer(), player.getUuid());
		List<InviteEntry> invites = new ArrayList<>();
		long now = System.currentTimeMillis();
		for (var world : player.getServer().getWorlds()) {
			for (FarmAreas.Finished farm : FarmAreas.of(world).finished()) {
				for (FarmInvite invite : farm.invites().values()) {
					if (invite.playerUuid().equals(player.getUuid()) && !invite.expired(now) && invites.size() < MAX_INVITES) {
						String name = farm.customName() == null ? farm.farmId() : farm.customName();
						invites.add(new InviteEntry(FarmKey.of(world, farm.instanceId()).externalForm(), invite.inviteId(), name, invite.role(), invite.expiresAt()));
					}
				}
			}
		}
		Platform.network().send(player, new LoadedFarmsPayload(farms.subList(0, Math.min(MAX_FARMS, farms.size())), List.copyOf(invites),
				DreamTableConfig.maxLoadedFarms()));
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}

	/** Asks for the list, picking or unpicking one farm first when {@code toggle} names it. */
	public record Request(BlockPos table, String toggle) implements CustomPayload {
		public static final Id<Request> ID = new Id<>(Identifier.of(TheDreamTableMod.MOD_ID, "loaded_farms_request"));
		public static final PacketCodec<RegistryByteBuf, Request> CODEC = PacketCodec.of(
				(request, buf) -> {
					buf.writeBlockPos(request.table());
					buf.writeString(request.toggle(), 256);
				},
				buf -> new Request(buf.readBlockPos(), buf.readString(256)));

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}
}
