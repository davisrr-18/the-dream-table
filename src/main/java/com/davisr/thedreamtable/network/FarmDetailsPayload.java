package com.davisr.thedreamtable.network;

import com.davisr.thedreamtable.TheDreamTableMod;
import com.davisr.thedreamtable.farm.FarmLoader;
import com.davisr.thedreamtable.farm.FarmStorage;
import com.davisr.thedreamtable.farm.FarmTemplate;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
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

/**
 * One finished farm's storage and size for its My Farms page: items stored against the room there is, how many
 * chests and barrels, the items held, most first, and the farm's block count. Sent only to the farm's owner.
 * Item counts travel apart from the items, since a total can be far above a stack.
 */
public record FarmDetailsPayload(String key, boolean loaded, int stored, int capacity, int containers, int blocks, List<String> items, List<Integer> counts)
		implements CustomPayload {
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
				buf.writeVarInt(details.items().size());
				for (int index = 0; index < details.items().size(); index++) {
					buf.writeString(details.items().get(index));
					buf.writeVarInt(details.counts().get(index));
				}
			},
			buf -> {
				String key = buf.readString();
				boolean loaded = buf.readBoolean();
				int stored = buf.readVarInt();
				int capacity = buf.readVarInt();
				int containers = buf.readVarInt();
				int blocks = buf.readVarInt();
				int size = Math.min(SHOWN_ITEMS, buf.readVarInt());
				List<String> items = new ArrayList<>();
				List<Integer> counts = new ArrayList<>();
				for (int index = 0; index < size; index++) {
					items.add(buf.readString());
					counts.add(buf.readVarInt());
				}
				return new FarmDetailsPayload(key, loaded, stored, capacity, containers, blocks, items, counts);
			});

	public static void register() {
		PayloadTypeRegistry.playS2C().register(ID, CODEC);
		PayloadTypeRegistry.playC2S().register(Request.ID, Request.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Request.ID, (payload, context) -> context.server().execute(() -> send(context.player(), payload.key())));
	}

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
			ServerPlayNetworking.send(player, contents == null
					? new FarmDetailsPayload(key, false, 0, 0, 0, blocks, items, counts)
					: new FarmDetailsPayload(key, true, contents.stored(), contents.capacity(), contents.containers(), blocks, items, counts));
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
