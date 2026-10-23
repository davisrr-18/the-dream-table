package com.davisr.thedreamtable.network;

import com.davisr.thedreamtable.TheDreamTableMod;
import com.davisr.thedreamtable.block.entity.DreamTableBlockEntity;
import com.davisr.thedreamtable.block.entity.TableSession;
import com.davisr.thedreamtable.farm.FarmGeometry;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public record PlaceFarmPayload(BlockPos table, BlockPos anchor, Direction facing) implements CustomPayload {
	public static final Id<PlaceFarmPayload> ID = new Id<>(Identifier.of(TheDreamTableMod.MOD_ID, "place_farm"));
	public static final PacketCodec<RegistryByteBuf, PlaceFarmPayload> CODEC = PacketCodec.tuple(
			BlockPos.PACKET_CODEC, PlaceFarmPayload::table,
			BlockPos.PACKET_CODEC, PlaceFarmPayload::anchor,
			Direction.PACKET_CODEC, PlaceFarmPayload::facing,
			PlaceFarmPayload::new
	);

	public static void register() {
		PayloadTypeRegistry.playC2S().register(ID, CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ID, (payload, context) -> context.server().execute(() -> {
			ServerPlayerEntity player = context.player();
			if (player.squaredDistanceTo(payload.table().toCenterPos()) > 64.0) {
				return;
			}
			if (!(player.getWorld().getBlockEntity(payload.table()) instanceof DreamTableBlockEntity blockEntity)) {
				return;
			}
			TableSession table = blockEntity.session(player);
			if (table.canResume()) {
				table.begin(player, false);
				return;
			}
			if (!DreamTableBlockEntity.PHASE_IDLE.equals(table.getPhase()) || table.getSelectedFarm().isEmpty()) {
				return;
			}
			Direction facing = horizontal(payload.facing(), player);
			BlockPos anchor = payload.anchor();
			if (!inRange(table.getPos(), anchor, player)) {
				player.sendMessage(Text.translatable("gui.the_dream_table.place_first"), true);
				return;
			}
			table.setPlacement(anchor, facing);
			table.begin(player, true);
		}));
	}

	static Direction horizontal(Direction facing, ServerPlayerEntity player) {
		return facing != null && facing.getAxis().isHorizontal() ? facing : player.getHorizontalFacing();
	}

	static boolean inRange(BlockPos table, BlockPos anchor, ServerPlayerEntity player) {
		if (anchor == null || anchor.equals(table)) {
			return false;
		}
		if (anchor.getY() < player.getWorld().getBottomY() || anchor.getY() >= player.getWorld().getTopY()) {
			return false;
		}
		return anchor.getSquaredDistance(table) <= (double) FarmGeometry.AIM_RANGE * FarmGeometry.AIM_RANGE;
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
