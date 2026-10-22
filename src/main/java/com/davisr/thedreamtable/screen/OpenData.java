package com.davisr.thedreamtable.screen;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.math.BlockPos;

public record OpenData(BlockPos pos, String farm, int page, int phase, int index) {
	public static final PacketCodec<RegistryByteBuf, OpenData> CODEC = PacketCodec.tuple(
			BlockPos.PACKET_CODEC, OpenData::pos,
			PacketCodecs.STRING, OpenData::farm,
			PacketCodecs.VAR_INT, OpenData::page,
			PacketCodecs.VAR_INT, OpenData::phase,
			PacketCodecs.VAR_INT, OpenData::index,
			OpenData::new
	);
}
