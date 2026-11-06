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

public record DepositMaterialPayload(BlockPos table) implements CustomPayload {
	public static final Id<DepositMaterialPayload> ID = new Id<>(new Identifier(TheDreamTableMod.MOD_ID, "deposit_material"));
	public static final PacketCodec<RegistryByteBuf, DepositMaterialPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.BLOCK_POS, DepositMaterialPayload::table,
			DepositMaterialPayload::new
	);

	/** The client sends at most one deposit per tick while clicking; fifteen a second is above any person's clicking. */
	private static final RequestThrottle THROTTLE = new RequestThrottle(15, RequestThrottle.DEFAULT_WINDOW_TICKS);

	public static void register() {
		THROTTLE.forgetOnDisconnect();
		Platform.network().registerClientToServer(ID, CODEC, (payload, player) -> {
			if (!THROTTLE.allow(player.getUuid(), player.getServer().getTicks())) {
				return;
			}
			// The client deposits from the in-world board with the menu closed, so no open menu is required here.
			if (player.isSpectator() || !player.isAlive() || player.squaredDistanceTo(payload.table().toCenterPos()) > 64) {
				return;
			}
			if (player.getWorld().getBlockEntity(payload.table()) instanceof DreamTableBlockEntity table) {
				table.payloadSession(player).offerFromHand(player);
			}
		});
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
