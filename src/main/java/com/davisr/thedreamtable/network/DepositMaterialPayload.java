package com.davisr.thedreamtable.network;

import com.davisr.thedreamtable.TheDreamTableMod;
import com.davisr.thedreamtable.block.entity.DreamTableBlockEntity;
import com.davisr.thedreamtable.platform.Platform;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public record DepositMaterialPayload(BlockPos table) implements CustomPayload {
	public static final Id<DepositMaterialPayload> ID = new Id<>(Identifier.of(TheDreamTableMod.MOD_ID, "deposit_material"));
	public static final PacketCodec<RegistryByteBuf, DepositMaterialPayload> CODEC = PacketCodec.tuple(
			BlockPos.PACKET_CODEC, DepositMaterialPayload::table,
			DepositMaterialPayload::new
	);

	public static void register() {
		Platform.network().registerClientToServer(ID, CODEC, (payload, player) -> {
			if (player.squaredDistanceTo(payload.table().toCenterPos()) > 64) {
				return;
			}
			if (player.getWorld().getBlockEntity(payload.table()) instanceof DreamTableBlockEntity table) {
				table.session(player).offerFromHand(player);
			}
		});
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
