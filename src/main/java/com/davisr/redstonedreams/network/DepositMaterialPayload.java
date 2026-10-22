package com.davisr.redstonedreams.network;

import com.davisr.redstonedreams.RedstoneDreamsMod;
import com.davisr.redstonedreams.block.entity.DreamTableBlockEntity;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public record DepositMaterialPayload(BlockPos table) implements CustomPayload {
	public static final Id<DepositMaterialPayload> ID = new Id<>(Identifier.of(RedstoneDreamsMod.MOD_ID, "deposit_material"));
	public static final PacketCodec<RegistryByteBuf, DepositMaterialPayload> CODEC = PacketCodec.tuple(
			BlockPos.PACKET_CODEC, DepositMaterialPayload::table,
			DepositMaterialPayload::new
	);

	public static void register() {
		PayloadTypeRegistry.playC2S().register(ID, CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ID, (payload, context) -> context.server().execute(() -> {
			var player = context.player();
			if (player.squaredDistanceTo(payload.table().toCenterPos()) > 64) {
				return;
			}
			if (player.getWorld().getBlockEntity(payload.table()) instanceof DreamTableBlockEntity table) {
				table.offerFromHand(player);
			}
		}));
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
