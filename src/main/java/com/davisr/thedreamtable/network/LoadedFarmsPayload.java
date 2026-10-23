package com.davisr.thedreamtable.network;

import com.davisr.thedreamtable.DreamTableConfig;
import com.davisr.thedreamtable.TheDreamTableMod;
import com.davisr.thedreamtable.farm.FarmLoader;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/** The player's finished farms for the Keep Farms Loaded page, and the server's limit. Sent only to that player. */
public record LoadedFarmsPayload(List<FarmLoader.Entry> farms, int limit) implements CustomPayload {
	public static final Id<LoadedFarmsPayload> ID = new Id<>(Identifier.of(TheDreamTableMod.MOD_ID, "loaded_farms"));
	private static final int MAX_FARMS = 512;
	private static final PacketCodec<RegistryByteBuf, FarmLoader.Entry> ENTRY = PacketCodec.tuple(
			PacketCodecs.STRING, FarmLoader.Entry::key,
			PacketCodecs.STRING, FarmLoader.Entry::farmId,
			PacketCodecs.STRING, FarmLoader.Entry::dimension,
			BlockPos.PACKET_CODEC, FarmLoader.Entry::where,
			PacketCodecs.BOOL, FarmLoader.Entry::loaded,
			FarmLoader.Entry::new
	);
	public static final PacketCodec<RegistryByteBuf, LoadedFarmsPayload> CODEC = PacketCodec.tuple(
			ENTRY.collect(PacketCodecs.toList(MAX_FARMS)), LoadedFarmsPayload::farms,
			PacketCodecs.VAR_INT, LoadedFarmsPayload::limit,
			LoadedFarmsPayload::new
	);

	public static void register() {
		PayloadTypeRegistry.playS2C().register(ID, CODEC);
		PayloadTypeRegistry.playC2S().register(Request.ID, Request.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Request.ID, (payload, context) -> context.server().execute(() -> {
			ServerPlayerEntity player = context.player();
			FarmLoader loader = FarmLoader.of(context.server());
			if (!payload.toggle().isEmpty() && !loader.toggle(player.getUuid(), payload.toggle())) {
				player.sendMessage(Text.translatable("gui.the_dream_table.loaded_full", DreamTableConfig.maxLoadedFarms()), true);
			}
			send(player);
		}));
	}

	public static void send(ServerPlayerEntity player) {
		List<FarmLoader.Entry> farms = FarmLoader.of(player.getServer()).farmsOf(player.getServer(), player.getUuid());
		ServerPlayNetworking.send(player, new LoadedFarmsPayload(farms.subList(0, Math.min(MAX_FARMS, farms.size())), DreamTableConfig.maxLoadedFarms()));
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}

	/** Asks for the list, picking or unpicking one farm first when {@code toggle} names it. */
	public record Request(String toggle) implements CustomPayload {
		public static final Id<Request> ID = new Id<>(Identifier.of(TheDreamTableMod.MOD_ID, "loaded_farms_request"));
		public static final PacketCodec<RegistryByteBuf, Request> CODEC = PacketCodec.tuple(
				PacketCodecs.STRING, Request::toggle,
				Request::new
		);

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}
}
