package com.davisr.thedreamtable.network;

import com.davisr.thedreamtable.TheDreamTableMod;
import com.davisr.thedreamtable.farm.FarmAreas;
import com.davisr.thedreamtable.farm.FarmAction;
import com.davisr.thedreamtable.farm.FarmDirectory;
import com.davisr.thedreamtable.farm.FarmLoader;
import com.davisr.thedreamtable.farm.FarmStorage;
import com.davisr.thedreamtable.farm.FarmTemplate;
import com.davisr.thedreamtable.platform.Platform;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One finished farm's storage, size and condition for its My Farms page: items stored against the room there is, how
 * many chests and barrels, the items held, most first, the farm's block count, and, for a repair, the blocks missing,
 * those the player changed, and the items the missing ones take.
 * Item counts travel apart from the items, since a total can be far above a stack.
 */
public record FarmDetailsPayload(String key, boolean loaded, int stored, int capacity, int containers, int blocks, List<String> items, List<Integer> counts,
		int missing, int changed, List<String> needs, List<Integer> needCounts) implements CustomPayload {
	public static final Id<FarmDetailsPayload> ID = new Id<>(Identifier.of(TheDreamTableMod.MOD_ID, "farm_details"));
	private static final int SHOWN_ITEMS = 8;
	/**
	 * Each accepted request scans one farm's storage and presence. A page asks once per selection, so four a second is far
	 * above a person's use. The answer is not cached: a repeat right after a change, like a repair, must
	 * see the change.
	 */
	static final int BUDGET = 4;
	private static final RequestThrottle THROTTLE = new RequestThrottle(BUDGET, RequestThrottle.DEFAULT_WINDOW_TICKS);

	public static final PacketCodec<RegistryByteBuf, FarmDetailsPayload> CODEC = PacketCodec.of(
			(details, buf) -> {
				buf.writeString(details.key(), 256);
				buf.writeBoolean(details.loaded());
				buf.writeVarInt(details.stored());
				buf.writeVarInt(details.capacity());
				buf.writeVarInt(details.containers());
				buf.writeVarInt(details.blocks());
				writeItems(buf, details.items(), details.counts());
				buf.writeVarInt(details.missing());
				buf.writeVarInt(details.changed());
				writeItems(buf, details.needs(), details.needCounts());
			},
			buf -> {
				String key = buf.readString(256);
				boolean loaded = buf.readBoolean();
				int stored = buf.readVarInt();
				int capacity = buf.readVarInt();
				int containers = buf.readVarInt();
				int blocks = buf.readVarInt();
				List<String> items = new ArrayList<>();
				List<Integer> counts = new ArrayList<>();
				readItems(buf, items, counts);
				int missing = buf.readVarInt();
				int changed = buf.readVarInt();
				List<String> needs = new ArrayList<>();
				List<Integer> needCounts = new ArrayList<>();
				readItems(buf, needs, needCounts);
				return new FarmDetailsPayload(key, loaded, stored, capacity, containers, blocks, items, counts, missing, changed, needs, needCounts);
			});

	private static void writeItems(RegistryByteBuf buf, List<String> items, List<Integer> counts) {
		buf.writeVarInt(items.size());
		for (int index = 0; index < items.size(); index++) {
			buf.writeString(items.get(index), 256);
			buf.writeVarInt(counts.get(index));
		}
	}

	private static void readItems(RegistryByteBuf buf, List<String> items, List<Integer> counts) {
		int size = buf.readVarInt();
		if (size < 0 || size > SHOWN_ITEMS) {
			throw new IllegalArgumentException("Invalid farm detail item count: " + size);
		}
		for (int index = 0; index < size; index++) {
			items.add(buf.readString(256));
			counts.add(buf.readVarInt());
		}
	}

	public static void register() {
		Platform.network().registerServerToClient(ID, CODEC);
		THROTTLE.forgetOnDisconnect();
		Platform.network().registerClientToServer(Request.ID, Request.CODEC, (payload, player) -> {
			if (!THROTTLE.allow(player.getUuid(), player.getServer().getTicks())) {
				return;
			}
			FarmNetwork.Checked checked = FarmNetwork.authorize(player, payload.table(), payload.key(), FarmAction.VIEW,
					FarmActionResultPayload.Action.DETAILS);
			if (checked != null) {
				send(player, checked);
			}
		});
	}

	/** Missing is {@link FarmLoader#UNKNOWN} while part of the farm is unloaded. */
	private static void send(ServerPlayerEntity player, FarmNetwork.Checked checked) {
		FarmDirectory.Located located = checked.farm();
		String key = located.key().externalForm();
		FarmLoader.Located farm = new FarmLoader.Located(key, located.world(), located.farm());
			FarmTemplate template = farm.farm().layout();
			FarmStorage.Contents contents = template == null ? null : FarmStorage.contents(farm.world(), template, farm.farm().site());
			List<String> items = new ArrayList<>();
			List<Integer> counts = new ArrayList<>();
			if (contents != null) {
				for (ItemStack stack : contents.items().subList(0, Math.min(SHOWN_ITEMS, contents.items().size()))) {
					items.add(Registries.ITEM.getId(stack.getItem()).toString());
					counts.add(stack.getCount());
				}
			}
			int blocks = template == null ? 0 : template.steps().size();
			int missing = FarmLoader.UNKNOWN;
			int changed = 0;
			List<String> needs = new ArrayList<>();
			List<Integer> needCounts = new ArrayList<>();
			if (template != null) {
				FarmAreas.Presence presence = FarmAreas.presence(farm.world(), template, farm.farm().site(), farm.farm().swaps());
				if (!presence.unloaded()) {
					missing = presence.missing();
					changed = presence.changed();
					presence.needs().entrySet().stream()
							.sorted(Map.Entry.<Item, Integer>comparingByValue().reversed())
							.limit(SHOWN_ITEMS)
							.forEach(need -> {
								needs.add(Registries.ITEM.getId(need.getKey()).toString());
								needCounts.add(need.getValue());
							});
				}
			}
			Platform.network().send(player, contents == null
					? new FarmDetailsPayload(key, false, 0, 0, 0, blocks, items, counts, missing, changed, needs, needCounts)
					: new FarmDetailsPayload(key, true, contents.stored(), contents.capacity(), contents.containers(), blocks, items, counts, missing, changed,
							needs, needCounts));
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}

	/** Asks for one farm's details while its My Farms page is open. */
	public record Request(BlockPos table, String key) implements CustomPayload {
		public static final Id<Request> ID = new Id<>(Identifier.of(TheDreamTableMod.MOD_ID, "farm_details_request"));
		public static final PacketCodec<RegistryByteBuf, Request> CODEC = PacketCodec.of(
				(request, buf) -> {
					buf.writeBlockPos(request.table());
					buf.writeString(request.key(), 256);
				},
				buf -> new Request(buf.readBlockPos(), buf.readString(256)));

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}
}
