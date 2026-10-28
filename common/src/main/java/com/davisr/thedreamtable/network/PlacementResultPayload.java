package com.davisr.thedreamtable.network;

import com.davisr.thedreamtable.TheDreamTableMod;
import com.davisr.thedreamtable.platform.Platform;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * The server's answer to {@link CheckPlacementPayload}: the lang key of the problem, or empty when the farm fits there,
 * and how much of the farm already stands there, in percent.
 */
public record PlacementResultPayload(BlockPos table, BlockPos anchor, Direction facing, String problem, int existing) implements CustomPayload {
	/** For the quiet rechecks: the farm already standing there was counted when the spot was picked. */
	public static final int UNCHANGED = -1;
	public static final Id<PlacementResultPayload> ID = new Id<>(Identifier.of(TheDreamTableMod.MOD_ID, "placement_result"));
	public static final PacketCodec<RegistryByteBuf, PlacementResultPayload> CODEC = PacketCodec.tuple(
			BlockPos.PACKET_CODEC, PlacementResultPayload::table,
			BlockPos.PACKET_CODEC, PlacementResultPayload::anchor,
			Direction.PACKET_CODEC, PlacementResultPayload::facing,
			PacketCodecs.STRING, PlacementResultPayload::problem,
			PacketCodecs.VAR_INT, PlacementResultPayload::existing,
			PlacementResultPayload::new
	);

	public static void register() {
		Platform.network().registerServerToClient(ID, CODEC);
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
