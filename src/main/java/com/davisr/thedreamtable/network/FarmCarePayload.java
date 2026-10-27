package com.davisr.thedreamtable.network;

import com.davisr.thedreamtable.TheDreamTableMod;
import com.davisr.thedreamtable.block.entity.DreamTableBlockEntity;
import com.davisr.thedreamtable.block.entity.FarmCare;
import com.davisr.thedreamtable.block.entity.TableSession;
import com.davisr.thedreamtable.farm.FarmAreas;
import com.davisr.thedreamtable.farm.FarmLoader;
import com.davisr.thedreamtable.screen.DreamTableScreenHandler;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** A My Farms action addressed by the farm's stable key, never by its current place in a changing list. */
public record FarmCarePayload(BlockPos table, String key, Action action) implements CustomPayload {
	private static final int MAX_KEY_LENGTH = 256;
	public static final Id<FarmCarePayload> ID = new Id<>(Identifier.of(TheDreamTableMod.MOD_ID, "farm_care"));
	public static final PacketCodec<RegistryByteBuf, FarmCarePayload> CODEC = PacketCodec.of(
			(payload, buf) -> {
				buf.writeBlockPos(payload.table());
				buf.writeString(payload.key(), MAX_KEY_LENGTH);
				buf.writeEnumConstant(payload.action());
			},
			buf -> new FarmCarePayload(buf.readBlockPos(), buf.readString(MAX_KEY_LENGTH), buf.readEnumConstant(Action.class)));

	public enum Action {
		REPAIR, TAKE_DOWN, FORGET
	}

	public static void register() {
		PayloadTypeRegistry.playC2S().register(ID, CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ID, (payload, context) -> context.server().execute(() -> handle(context.player(), payload)));
	}

	private static void handle(ServerPlayerEntity player, FarmCarePayload payload) {
		if (!(player.currentScreenHandler instanceof DreamTableScreenHandler handler)
				|| !handler.getTablePos().equals(payload.table()) || !handler.canUse(player)) {
			return;
		}
		if (!(player.getWorld().getBlockEntity(payload.table()) instanceof DreamTableBlockEntity table)) {
			return;
		}
		TableSession session = table.session(player);
		FarmLoader.of(player.getServer()).byKey(player.getServer(), player.getUuid(), payload.key()).ifPresent(farm -> {
			switch (payload.action()) {
				case REPAIR -> FarmCare.repair(session, player, farm);
				case TAKE_DOWN -> FarmCare.takeDown(session, player, farm);
				case FORGET -> {
					if (farm.farm().layout() == null) {
						FarmAreas.of(farm.world()).release(farm.farm().key());
					}
				}
			}
		});
		LoadedFarmsPayload.send(player);
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
