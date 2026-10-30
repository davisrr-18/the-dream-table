package com.davisr.thedreamtable.network;

import com.davisr.thedreamtable.TheDreamTableMod;
import com.davisr.thedreamtable.farm.FarmAction;
import com.davisr.thedreamtable.farm.FarmPreferences;
import com.davisr.thedreamtable.platform.Platform;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/** Confirms only event references issued in the sender's current alert page. */
public record FarmAlertAckPayload(BlockPos table, String key, String token, List<Long> eventIds) implements CustomPayload {
	public static final Id<FarmAlertAckPayload> ID = new Id<>(Identifier.of(TheDreamTableMod.MOD_ID, "farm_alert_ack"));
	private static final int MAX_EVENT_IDS = 50;
	public static final PacketCodec<RegistryByteBuf, FarmAlertAckPayload> CODEC = PacketCodec.of(
			(payload, buf) -> {
				buf.writeBlockPos(payload.table());
				buf.writeString(payload.key(), 256);
				buf.writeString(payload.token(), 64);
				buf.writeVarInt(payload.eventIds().size());
				payload.eventIds().forEach(buf::writeVarLong);
			},
			buf -> {
				BlockPos table = buf.readBlockPos();
				String key = buf.readString(256);
				String token = buf.readString(64);
				int size = buf.readVarInt();
				if (size < 0 || size > MAX_EVENT_IDS) {
					throw new IllegalArgumentException("Invalid alert acknowledgement count: " + size);
				}
				List<Long> eventIds = new ArrayList<>(size);
				for (int index = 0; index < size; index++) {
					eventIds.add(buf.readVarLong());
				}
				return new FarmAlertAckPayload(table, key, token, List.copyOf(eventIds));
			});

	public static void register() {
		Platform.network().registerClientToServer(ID, CODEC, (payload, player) -> acknowledge(player, payload));
	}

	private static void acknowledge(ServerPlayerEntity player, FarmAlertAckPayload payload) {
		FarmNetwork.Checked checked = FarmNetwork.authorize(player, payload.table(), payload.key(), FarmAction.ALERTS,
				FarmActionResultPayload.Action.DETAILS);
		if (checked == null || !FarmAlertsPayload.accepts(player, payload.token(), payload.key(), payload.eventIds())) {
			return;
		}
		long cursor = payload.eventIds().stream().mapToLong(Long::longValue).max().orElse(0L);
		if (cursor > 0) {
			FarmPreferences.of(player.getServer()).acknowledge(player.getUuid(), checked.farm().key(), cursor);
		}
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
