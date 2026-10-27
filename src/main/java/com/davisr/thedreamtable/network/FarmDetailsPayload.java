package com.davisr.thedreamtable.network;

import com.davisr.thedreamtable.TheDreamTableMod;
import com.davisr.thedreamtable.farm.FarmAreas;
import com.davisr.thedreamtable.farm.FarmLoader;
import com.davisr.thedreamtable.farm.FarmStorage;
import com.davisr.thedreamtable.farm.FarmTemplate;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One finished farm's storage, size and condition for its My Farms page: items stored against the room there is, how
 * many chests and barrels, the items held, most first, the farm's block count, and, for a repair, the blocks missing,
 * those the player changed, and the items the missing ones take. Sent only to the farm's owner.
 * Item counts travel apart from the items, since a total can be far above a stack.
 */
public record FarmDetailsPayload(String key, boolean loaded, int stored, int capacity, int containers, int blocks, List<String> items, List<Integer> counts,
		int missing, int changed, List<String> needs, List<Integer> needCounts) implements CustomPayload {
	public static final Id<FarmDetailsPayload> ID = new Id<>(Identifier.of(TheDreamTableMod.MOD_ID, "farm_details"));
	private static final int SHOWN_ITEMS = 8;
	public static final PacketCodec<RegistryByteBuf, FarmDetailsPayload> CODEC = PacketCodec.of(
			(details, buf) -> {
				buf.writeString(details.key());
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
				String key = buf.readString();
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
			buf.writeString(items.get(index));
			buf.writeVarInt(counts.get(index));
		}
	}

	private static void readItems(RegistryByteBuf buf, List<String> items, List<Integer> counts) {
		int size = Math.min(SHOWN_ITEMS, buf.readVarInt());
		for (int index = 0; index < size; index++) {
			items.add(buf.readString());
			counts.add(buf.readVarInt());
		}
	}

	public static void register() {
		PayloadTypeRegistry.playS2C().register(ID, CODEC);
		PayloadTypeRegistry.playC2S().register(Request.ID, Request.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Request.ID, (payload, context) -> context.server().execute(() -> send(context.player(), payload.key())));
	}

	/** Missing is {@link FarmLoader#UNKNOWN} while part of the farm is unloaded. */
	private static void send(ServerPlayerEntity player, String key) {
		FarmLoader.of(player.getServer()).byKey(player.getServer(), player.getUuid(), key).ifPresent(farm -> {
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
			ServerPlayNetworking.send(player, contents == null
					? new FarmDetailsPayload(key, false, 0, 0, 0, blocks, items, counts, missing, changed, needs, needCounts)
					: new FarmDetailsPayload(key, true, contents.stored(), contents.capacity(), contents.containers(), blocks, items, counts, missing, changed,
							needs, needCounts));
		});
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}

	/** Asks for one farm's details while its My Farms page is open. */
	public record Request(String key) implements CustomPayload {
		public static final Id<Request> ID = new Id<>(Identifier.of(TheDreamTableMod.MOD_ID, "farm_details_request"));
		public static final PacketCodec<RegistryByteBuf, Request> CODEC = PacketCodec.tuple(
				PacketCodecs.STRING, Request::key,
				Request::new
		);

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}
}
