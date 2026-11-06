package com.davisr.thedreamtable.network;

import com.davisr.thedreamtable.network.TableNoticePayload;
import com.davisr.thedreamtable.TheDreamTableMod;
import com.davisr.thedreamtable.block.entity.DreamTableBlockEntity;
import com.davisr.thedreamtable.block.entity.TableSession;
import com.davisr.thedreamtable.farm.FarmGeometry;
import com.davisr.thedreamtable.platform.Platform;
import com.davisr.thedreamtable.network.codec.RegistryByteBuf;
import com.davisr.thedreamtable.network.codec.PacketCodec;
import com.davisr.thedreamtable.network.codec.PacketCodecs;
import com.davisr.thedreamtable.network.codec.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public record PlaceFarmPayload(BlockPos table, BlockPos anchor, Direction facing, boolean mirrored) implements CustomPayload {
	public static final Id<PlaceFarmPayload> ID = new Id<>(new Identifier(TheDreamTableMod.MOD_ID, "place_farm"));
	public static final PacketCodec<RegistryByteBuf, PlaceFarmPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.BLOCK_POS, PlaceFarmPayload::table,
			PacketCodecs.BLOCK_POS, PlaceFarmPayload::anchor,
			PacketCodecs.DIRECTION, PlaceFarmPayload::facing,
			PacketCodecs.BOOL, PlaceFarmPayload::mirrored,
			PlaceFarmPayload::new
	);

	/**
	 * Beginning a farm checks every step of the template. A person places once and the phase then leaves idle, or re-aims
	 * after a refusal, so three requests a second leave normal use untouched.
	 */
	private static final RequestThrottle THROTTLE = new RequestThrottle(3, RequestThrottle.DEFAULT_WINDOW_TICKS);

	public static void register() {
		THROTTLE.forgetOnDisconnect();
		Platform.network().registerClientToServer(ID, CODEC, (payload, player) -> {
			if (!THROTTLE.allow(player.getUuid(), player.getServer().getTicks()) || player.isSpectator() || player.squaredDistanceTo(payload.table().toCenterPos()) > 64.0) {
				return;
			}
			DreamTableBlockEntity blockEntity = FarmNetwork.table(player, payload.table());
			if (blockEntity == null) {
				return;
			}
			TableSession table = blockEntity.payloadSession(player);
			if (table.build().canResume()) {
				table.build().begin(player, false);
				return;
			}
			if (!DreamTableBlockEntity.PHASE_IDLE.equals(table.getPhase()) || table.getSelectedFarm().isEmpty()) {
				return;
			}
			Direction facing = horizontal(payload.facing(), player);
			BlockPos anchor = payload.anchor();
			if (!inRange(table.getPos(), anchor, player)) {
				TableNoticePayload.problem(player, Text.translatable("gui.the_dream_table.place_first"));
				return;
			}
			table.setPlacement(anchor, facing, payload.mirrored());
			table.rememberAim(player.getWorld().getRegistryKey().getValue(), anchor, facing, payload.mirrored());
			if (!table.build().begin(player, true)) {
				// A refused start keeps the spot as the aim, not as a placement that would reserve the area.
				table.clearPlacement();
			}
		});
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
		return FarmGeometry.withinReach(table, anchor);
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
