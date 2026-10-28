package com.davisr.thedreamtable.network;

import com.davisr.thedreamtable.TheDreamTableMod;
import com.davisr.thedreamtable.block.entity.DreamTableBlockEntity;
import com.davisr.thedreamtable.platform.Platform;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** Sent by a client whose copy of a table has no session yet, such as after the table's chunk loaded again. */
public record RequestSessionPayload(BlockPos table) implements CustomPayload {
	public static final Id<RequestSessionPayload> ID = new Id<>(Identifier.of(TheDreamTableMod.MOD_ID, "request_session"));
	public static final PacketCodec<RegistryByteBuf, RequestSessionPayload> CODEC = PacketCodec.tuple(
			BlockPos.PACKET_CODEC, RequestSessionPayload::table,
			RequestSessionPayload::new
	);
	/** The client asks for tracked and pinned tables far beyond aiming range, so the reach covers them. */
	private static final double MAX_DISTANCE = 256.0;

	public static void register() {
		Platform.network().registerClientToServer(ID, CODEC, (payload, player) -> {
			if (player.squaredDistanceTo(payload.table().toCenterPos()) > MAX_DISTANCE * MAX_DISTANCE) {
				return;
			}
			if (player.getWorld().isChunkLoaded(payload.table()) && player.getWorld().getBlockEntity(payload.table()) instanceof DreamTableBlockEntity table) {
				table.resend(player);
			}
		});
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
