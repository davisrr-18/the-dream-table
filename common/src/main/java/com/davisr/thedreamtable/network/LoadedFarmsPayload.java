package com.davisr.thedreamtable.network;

import com.davisr.thedreamtable.network.TableNoticePayload;
import com.davisr.thedreamtable.DreamTableConfig;
import com.davisr.thedreamtable.TheDreamTableMod;
import com.davisr.thedreamtable.farm.FarmLoader;
import com.davisr.thedreamtable.farm.FarmAction;
import com.davisr.thedreamtable.platform.Platform;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/** The player's finished farms for the My Farms pages, and the server's limit. Sent only to that player. */
public record LoadedFarmsPayload(List<FarmLoader.Entry> farms, int limit) implements CustomPayload {
	public static final Id<LoadedFarmsPayload> ID = new Id<>(new Identifier(TheDreamTableMod.MOD_ID, "loaded_farms"));
	private static final int MAX_FARMS = 512;
	/** Farm and layout ids embed imported file names; the limit is far above any real one and encodes the same below 256. */
	static final int MAX_ID = 1024;
	/**
	 * A plain list read scans every farm the player may see. The page asks once per visit and again every second until
	 * the list arrives, so four a second leaves room for retries and nothing more.
	 */
	static final int LIST_BUDGET = 4;
	/** A list built less than this many ticks ago answers a plain list read without a new scan; every send, refreshes included, replaces it. */
	static final int LIST_CACHE_TICKS = 10;
	private static final RequestThrottle THROTTLE = new RequestThrottle(LIST_BUDGET, RequestThrottle.DEFAULT_WINDOW_TICKS);
	private static final RecentResults<Boolean, LoadedFarmsPayload> RECENT = new RecentResults<>(LIST_CACHE_TICKS);
	/** A toggle is a button click that changes server state and gets a reply: six a second is more than a person makes. */
	private static final RequestThrottle TOGGLE_THROTTLE = new RequestThrottle(6, RequestThrottle.DEFAULT_WINDOW_TICKS);
	private static final PacketCodec<RegistryByteBuf, FarmLoader.Entry> ENTRY = PacketCodec.of(
			(entry, buf) -> {
				buf.writeString(entry.key(), 256);
				buf.writeString(entry.farmId(), MAX_ID);
				buf.writeString(entry.layoutId(), MAX_ID);
				buf.writeString(entry.customName(), 128);
				buf.writeString(entry.dimension(), 256);
				buf.writeBlockPos(entry.where());
				buf.writeBoolean(entry.favorite());
				buf.writeBoolean(entry.tracked());
				buf.writeBoolean(entry.loaded());
				buf.writeBoolean(entry.ownerQuotaFull());
				buf.writeVarInt(entry.status());
				buf.writeVarInt(entry.missing() + 1);
				buf.writeBoolean(entry.available());
				buf.writeVarInt(entry.storage() + 1);
				buf.writeVarInt(entry.alerts());
			},
			buf -> new FarmLoader.Entry(buf.readString(256), buf.readString(MAX_ID), buf.readString(MAX_ID), buf.readString(128), buf.readString(256), buf.readBlockPos(),
					buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
					buf.readVarInt(), buf.readVarInt() - 1, buf.readBoolean(), buf.readVarInt() - 1, buf.readVarInt()));
	public static final PacketCodec<RegistryByteBuf, LoadedFarmsPayload> CODEC = PacketCodec.tuple(
			ENTRY.collect(PacketCodecs.toList(MAX_FARMS)), LoadedFarmsPayload::farms,
			PacketCodecs.VAR_INT, LoadedFarmsPayload::limit,
			LoadedFarmsPayload::new
	);

	public static void register() {
		Platform.network().registerServerToClient(ID, CODEC);
		THROTTLE.forgetOnDisconnect();
		TOGGLE_THROTTLE.forgetOnDisconnect();
		RECENT.forgetOnDisconnect();
		FarmNetwork.register();
		Platform.network().registerClientToServer(Request.ID, Request.CODEC, (payload, player) -> {
			boolean toggling = !payload.toggle().isEmpty();
			long now = player.getServer().getTicks();
			LoadedFarmsGate.Route route = LoadedFarmsGate.route(toggling, (toggling ? TOGGLE_THROTTLE : THROTTLE).allow(player.getUuid(), now));
			if (route == LoadedFarmsGate.Route.DROP || FarmNetwork.table(player, payload.table()) == null) {
				return;
			}
			if (route == LoadedFarmsGate.Route.BUSY) {
				FarmNetwork.result(player, payload.toggle(), FarmActionResultPayload.Action.KEEP_LOADED, FarmActionResultPayload.Result.BUSY);
				return;
			}
			if (route == LoadedFarmsGate.Route.LIST) {
				LoadedFarmsPayload recent = RECENT.get(player.getUuid(), Boolean.TRUE, now);
				if (recent != null) {
					Platform.network().send(player, recent);
				} else {
					send(player);
				}
				return;
			}
			FarmNetwork.Checked checked = FarmNetwork.authorize(player, payload.table(), payload.toggle(), FarmAction.KEEP_LOADED,
					FarmActionResultPayload.Action.KEEP_LOADED);
			if (checked != null) {
				FarmLoader.ToggleResult result = FarmLoader.of(player.getServer()).toggle(player.getServer(), player.getUuid(), checked.farm().key());
				FarmActionResultPayload.Result response = switch (result) {
					case SELECTED, UNSELECTED -> FarmActionResultPayload.Result.SUCCESS;
					case OWNER_QUOTA_FULL, TOO_LARGE -> FarmActionResultPayload.Result.LIMIT_REACHED;
					case NOT_FOUND -> FarmActionResultPayload.Result.NOT_FOUND;
					case FORBIDDEN -> FarmActionResultPayload.Result.FORBIDDEN;
				};
				FarmNetwork.result(player, payload.toggle(), FarmActionResultPayload.Action.KEEP_LOADED, response);
				if (result == FarmLoader.ToggleResult.OWNER_QUOTA_FULL) {
					TableNoticePayload.problem(player, Text.translatable("gui.the_dream_table.loaded_full", DreamTableConfig.maxLoadedFarms()));
				} else if (result == FarmLoader.ToggleResult.TOO_LARGE) {
					TableNoticePayload.problem(player, Text.translatable("gui.the_dream_table.loaded_too_large", FarmLoader.MAX_CHUNKS_PER_FARM));
				}
			}
			// The full scan after a toggle, granted or denied, runs only through the shared per-player refresh budget.
			FarmNetwork.refresh(player);
		});
	}

	public static void send(ServerPlayerEntity player) {
		// A player whose client can't take the list (or who has no connection, like a fake player) costs no scan.
		if (!Platform.network().canSend(player, ID)) {
			return;
		}
		List<FarmLoader.Entry> farms = FarmLoader.of(player.getServer()).farmsOf(player.getServer(), player.getUuid());
		LoadedFarmsPayload list = new LoadedFarmsPayload(List.copyOf(farms.subList(0, Math.min(MAX_FARMS, farms.size()))), DreamTableConfig.maxLoadedFarms());
		RECENT.put(player.getUuid(), Boolean.TRUE, list, player.getServer().getTicks());
		Platform.network().send(player, list);
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}

	/** Asks for the list, picking or unpicking one farm first when {@code toggle} names it. */
	public record Request(BlockPos table, String toggle) implements CustomPayload {
		public static final Id<Request> ID = new Id<>(new Identifier(TheDreamTableMod.MOD_ID, "loaded_farms_request"));
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
