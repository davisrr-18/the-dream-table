package com.davisr.thedreamtable.network;

import com.davisr.thedreamtable.TheDreamTableMod;
import com.davisr.thedreamtable.block.entity.DreamTableBlockEntity;
import com.davisr.thedreamtable.platform.Platform;
import com.davisr.thedreamtable.network.codec.RegistryByteBuf;
import com.davisr.thedreamtable.network.codec.PacketCodec;
import com.davisr.thedreamtable.network.codec.PacketCodecs;
import com.davisr.thedreamtable.network.codec.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** Sent by a client whose copy of a table has no session yet, such as after the table's chunk loaded again. */
public record RequestSessionPayload(BlockPos table) implements CustomPayload {
	public static final Id<RequestSessionPayload> ID = new Id<>(new Identifier(TheDreamTableMod.MOD_ID, "request_session"));
	public static final PacketCodec<RegistryByteBuf, RequestSessionPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.BLOCK_POS, RequestSessionPayload::table,
			RequestSessionPayload::new
	);
	/** The client asks for tracked and pinned tables far beyond aiming range, so the reach covers them. */
	private static final double MAX_DISTANCE = 256.0;

	/**
	 * The client asks for up to three tables once a second until each answers, so six a second covers that with room for
	 * a short burst after a chunk reload; more is a modified client asking for resends.
	 */
	private static final RequestThrottle THROTTLE = new RequestThrottle(6, RequestThrottle.DEFAULT_WINDOW_TICKS);

	public static void register() {
		THROTTLE.forgetOnDisconnect();
		Platform.network().registerClientToServer(ID, CODEC, (payload, player) -> {
			if (!THROTTLE.allow(player.getUuid(), player.getServer().getTicks())) {
				return;
			}
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
