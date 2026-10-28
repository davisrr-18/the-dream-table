package com.davisr.thedreamtable.network;

import com.davisr.thedreamtable.TheDreamTableMod;
import com.davisr.thedreamtable.farm.FarmAreas;
import com.davisr.thedreamtable.platform.Platform;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Other players' farm areas near a player who turned on "Show Other Farms", sent every two seconds.
 * Only the box and whether it is still being built go out: no owner, farm or materials.
 */
public record FarmAreasPayload(List<Area> areas) implements CustomPayload {
	public static final Id<FarmAreasPayload> ID = new Id<>(Identifier.of(TheDreamTableMod.MOD_ID, "farm_areas"));
	private static final int MAX_AREAS = 256;
	private static final int RADIUS = 160;
	private static final int INTERVAL_TICKS = 40;
	private static final Set<UUID> WATCHERS = new HashSet<>();
	/** The immediate first send of turning the option on scans the area index; the two-second tick covers a throttled one. */
	private static final RequestThrottle STARTS = new RequestThrottle(3, 100);

	public record Area(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, boolean finished) {
		private static final PacketCodec<RegistryByteBuf, Area> CODEC = PacketCodec.of(
				(area, buf) -> {
					buf.writeInt(area.minX).writeInt(area.minY).writeInt(area.minZ);
					buf.writeInt(area.maxX).writeInt(area.maxY).writeInt(area.maxZ);
					buf.writeBoolean(area.finished);
				},
				buf -> new Area(buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readBoolean()));

		public Box box() {
			return new Box(minX, minY, minZ, maxX, maxY, maxZ);
		}

		static Area of(FarmAreas.View view) {
			Box box = view.box();
			return new Area((int) box.minX, (int) box.minY, (int) box.minZ, (int) box.maxX, (int) box.maxY, (int) box.maxZ,
					view.kind() == FarmAreas.Kind.FINISHED);
		}
	}

	public static final PacketCodec<RegistryByteBuf, FarmAreasPayload> CODEC = PacketCodec.tuple(
			Area.CODEC.collect(PacketCodecs.toList(MAX_AREAS)), FarmAreasPayload::areas,
			FarmAreasPayload::new
	);

	public static void register() {
		Platform.network().registerServerToClient(ID, CODEC);
		STARTS.forgetOnDisconnect();
		Platform.network().registerClientToServer(WatchAreasPayload.ID, WatchAreasPayload.CODEC, (payload, player) -> {
			if (payload.watch()) {
				WATCHERS.add(player.getUuid());
				if (STARTS.allow(player.getUuid(), player.getServer().getTicks())) {
					send(player);
				}
			} else {
				WATCHERS.remove(player.getUuid());
			}
		});
		Platform.serverEvents().onPlayerDisconnect((player, server) -> server.execute(() -> WATCHERS.remove(player.getUuid())));
		Platform.serverEvents().onTickEnd(FarmAreasPayload::tick);
	}

	private static void tick(MinecraftServer server) {
		if (WATCHERS.isEmpty() || server.getTicks() % INTERVAL_TICKS != 0) {
			return;
		}
		for (UUID watcher : List.copyOf(WATCHERS)) {
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(watcher);
			if (player != null) {
				send(player);
			}
		}
	}

	private static void send(ServerPlayerEntity player) {
		List<Area> areas = new ArrayList<>();
		for (FarmAreas.View view : FarmAreas.of(player.getServerWorld()).near(player.getBlockPos(), RADIUS, player.getUuid())) {
			if (areas.size() >= MAX_AREAS) {
				break;
			}
			areas.add(Area.of(view));
		}
		Platform.network().send(player, new FarmAreasPayload(areas));
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}

	/** Turns the area updates on or off for the sending player, following their Options setting. */
	public record WatchAreasPayload(boolean watch) implements CustomPayload {
		public static final Id<WatchAreasPayload> ID = new Id<>(Identifier.of(TheDreamTableMod.MOD_ID, "watch_areas"));
		public static final PacketCodec<RegistryByteBuf, WatchAreasPayload> CODEC = PacketCodec.tuple(
				PacketCodecs.BOOL, WatchAreasPayload::watch,
				WatchAreasPayload::new
		);

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}
}
