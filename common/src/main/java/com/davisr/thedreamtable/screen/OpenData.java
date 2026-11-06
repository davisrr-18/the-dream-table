package com.davisr.thedreamtable.screen;

import com.davisr.thedreamtable.network.codec.RegistryByteBuf;
import com.davisr.thedreamtable.network.codec.PacketCodec;
import com.davisr.thedreamtable.network.codec.PacketCodecs;
import net.minecraft.util.math.BlockPos;

public record OpenData(BlockPos pos, String farm, int page, int phase, int index) {
	public static final PacketCodec<RegistryByteBuf, OpenData> CODEC = PacketCodec.tuple(
			PacketCodecs.BLOCK_POS, OpenData::pos,
			PacketCodecs.STRING, OpenData::farm,
			PacketCodecs.VAR_INT, OpenData::page,
			PacketCodecs.VAR_INT, OpenData::phase,
			PacketCodecs.VAR_INT, OpenData::index,
			OpenData::new
	);
}
