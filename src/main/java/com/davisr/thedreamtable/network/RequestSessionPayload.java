package com.davisr.thedreamtable.network;

import com.davisr.thedreamtable.TheDreamTableMod;
import com.davisr.thedreamtable.block.entity.DreamTableBlockEntity;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** Sent by a client whose copy of a table has no session yet, such as after the table's chunk loaded again. */
public record RequestSessionPayload(BlockPos table) implements CustomPayload {
	public static final Id<RequestSessionPayload> ID = new Id<>(Identifier.of(TheDreamTableMod.MOD_ID, "request_session"));
	public static final PacketCodec<RegistryByteBuf, RequestSessionPayload> CODEC = PacketCodec.tuple(
			BlockPos.PACKET_CODEC, RequestSessionPayload::table,
			RequestSessionPayload::new
	);
	private static final double MAX_DISTANCE = 256.0;

	public static void register() {
		PayloadTypeRegistry.playC2S().register(ID, CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ID, (payload, context) -> context.server().execute(() -> {
			ServerPlayerEntity player = context.player();
			if (player.squaredDistanceTo(payload.table().toCenterPos()) > MAX_DISTANCE * MAX_DISTANCE) {
				return;
			}
			if (player.getWorld().isChunkLoaded(payload.table()) && player.getWorld().getBlockEntity(payload.table()) instanceof DreamTableBlockEntity table) {
				table.resend(player);
			}
		}));
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
