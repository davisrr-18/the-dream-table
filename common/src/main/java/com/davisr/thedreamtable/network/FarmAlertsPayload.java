package com.davisr.thedreamtable.network;

import com.davisr.thedreamtable.TheDreamTableMod;
import com.davisr.thedreamtable.farm.FarmAction;
import com.davisr.thedreamtable.farm.FarmAreas;
import com.davisr.thedreamtable.farm.alert.FarmAlertView;
import com.davisr.thedreamtable.platform.Platform;
import com.davisr.thedreamtable.network.codec.RegistryByteBuf;
import com.davisr.thedreamtable.network.codec.PacketCodec;
import com.davisr.thedreamtable.network.codec.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/** A server-authorized page of one farm's alerts, with a capability token for exact read acknowledgement. */
public record FarmAlertsPayload(String key, String token, List<Entry> entries) implements CustomPayload {
	public static final Id<FarmAlertsPayload> ID = new Id<>(new Identifier(TheDreamTableMod.MOD_ID, "farm_alerts"));
	private static final int MAX_ENTRIES = 50;
	private static final FarmAlertPageTokens TOKENS = new FarmAlertPageTokens();
	/** A page asks once per selection and each request builds a page of up to 50 alerts: four a second is far above a person's use. */
	private static final RequestThrottle THROTTLE = new RequestThrottle(4, RequestThrottle.DEFAULT_WINDOW_TICKS);

	public record Entry(long eventId, String type, int quantity, long time, boolean active) {
	}

	public static final PacketCodec<RegistryByteBuf, FarmAlertsPayload> CODEC = PacketCodec.of(
			(payload, buf) -> {
				buf.writeString(payload.key(), 256);
				buf.writeString(payload.token(), 64);
				buf.writeVarInt(payload.entries().size());
				for (Entry entry : payload.entries()) {
					buf.writeVarLong(entry.eventId());
					buf.writeString(entry.type(), 64);
					buf.writeVarInt(entry.quantity());
					buf.writeVarLong(entry.time());
					buf.writeBoolean(entry.active());
				}
			},
			buf -> {
				String key = buf.readString(256);
				String token = buf.readString(64);
				int size = buf.readVarInt();
				if (size < 0 || size > MAX_ENTRIES) {
					throw new IllegalArgumentException("Invalid farm alert page size: " + size);
				}
				List<Entry> entries = new ArrayList<>(size);
				for (int index = 0; index < size; index++) {
					entries.add(new Entry(buf.readVarLong(), buf.readString(64), buf.readVarInt(), buf.readVarLong(), buf.readBoolean()));
				}
				return new FarmAlertsPayload(key, token, List.copyOf(entries));
			});

	public static void register() {
		Platform.network().registerServerToClient(ID, CODEC);
		THROTTLE.forgetOnDisconnect();
		Platform.serverEvents().onPlayerDisconnect((player, server) -> server.execute(() -> TOKENS.forget(player.getUuid())));
		Platform.network().registerClientToServer(Request.ID, Request.CODEC, (payload, player) -> {
			if (THROTTLE.allow(player.getUuid(), player.getServer().getTicks())) {
				send(player, payload);
			}
		});
	}

	private static void send(ServerPlayerEntity player, Request request) {
		FarmNetwork.Checked checked = FarmNetwork.authorize(player, request.table(), request.key(), FarmAction.ALERTS,
				FarmActionResultPayload.Action.DETAILS);
		if (checked == null) {
			return;
		}
		List<FarmAlertView.Entry> page = FarmAreas.of(checked.farm().world()).alerts(checked.farm().farm().instanceId())
				.map(alerts -> FarmAlertView.page(alerts, request.offset(), MAX_ENTRIES)).orElse(List.of());
		List<Entry> entries = page.stream().map(entry -> new Entry(entry.event().eventId(), entry.event().type().name(), entry.event().quantity(), entry.event().time(), entry.active())).toList();
		String token = TOKENS.issue(player.getUuid(), request.key(), entries.stream().map(Entry::eventId).toList());
		Platform.network().send(player, new FarmAlertsPayload(request.key(), token, entries));
	}

	static boolean accepts(ServerPlayerEntity player, String token, String key, List<Long> eventIds) {
		return TOKENS.accept(player.getUuid(), token, key, eventIds);
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}

	public record Request(BlockPos table, String key, int offset) implements CustomPayload {
		public static final Id<Request> ID = new Id<>(new Identifier(TheDreamTableMod.MOD_ID, "farm_alerts_request"));
		public static final PacketCodec<RegistryByteBuf, Request> CODEC = PacketCodec.of(
				(request, buf) -> {
					buf.writeBlockPos(request.table());
					buf.writeString(request.key(), 256);
					buf.writeVarInt(Math.max(0, request.offset()));
				},
				buf -> new Request(buf.readBlockPos(), buf.readString(256), buf.readVarInt()));

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}
}
