package com.davisr.dreamtablepoc.network;

import com.davisr.dreamtablepoc.DreamTablePoc;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public record IncrementRequestPayload(BlockPos pos) implements CustomPayload {
	public static final Id<IncrementRequestPayload> ID = new Id<>(Identifier.of(DreamTablePoc.MOD_ID, "increment_request"));
	public static final PacketCodec<RegistryByteBuf, IncrementRequestPayload> CODEC =
			PacketCodec.tuple(BlockPos.PACKET_CODEC, IncrementRequestPayload::pos, IncrementRequestPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
