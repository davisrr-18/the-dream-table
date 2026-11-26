package com.davisr.thedreamtable.network;

import com.davisr.thedreamtable.TheDreamTableMod;
import com.davisr.thedreamtable.block.entity.DreamTableBlockEntity;
import com.davisr.thedreamtable.block.entity.TableSession;
import com.davisr.thedreamtable.farm.FarmSite;
import com.davisr.thedreamtable.platform.Platform;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * The Queue button: lines up the selected farm at the aimed spot instead of building it, so the table builds it after the
 * farms before it. Checked like {@link PlaceFarmPayload}, and the session adds the queue's own limit and overlap rules.
 */
public record QueueFarmPayload(BlockPos table, BlockPos anchor, Direction facing, boolean mirrored) implements CustomPayload {
	public static final Id<QueueFarmPayload> ID = new Id<>(Identifier.of(TheDreamTableMod.MOD_ID, "queue_farm"));
	public static final PacketCodec<RegistryByteBuf, QueueFarmPayload> CODEC = PacketCodec.tuple(
			BlockPos.PACKET_CODEC, QueueFarmPayload::table,
			BlockPos.PACKET_CODEC, QueueFarmPayload::anchor,
			Direction.PACKET_CODEC, QueueFarmPayload::facing,
			PacketCodecs.BOOL, QueueFarmPayload::mirrored,
			QueueFarmPayload::new
	);

	public static void register() {
		// One budget for placing and queueing: both check every step of the template, and a player can't double it by alternating.
		Platform.network().registerClientToServer(ID, CODEC, (payload, player) -> {
			if (!PlaceFarmPayload.THROTTLE.allow(player.getUuid(), player.getServer().getTicks()) || player.isSpectator() || player.squaredDistanceTo(payload.table().toCenterPos()) > 64.0) {
				return;
			}
			DreamTableBlockEntity blockEntity = FarmNetwork.table(player, payload.table());
			if (blockEntity == null) {
				return;
			}
			TableSession table = blockEntity.payloadSession(player);
			if (!DreamTableBlockEntity.PHASE_IDLE.equals(table.getPhase()) || table.getSelectedFarm().isEmpty()) {
				return;
			}
			Direction facing = PlaceFarmPayload.horizontal(payload.facing(), player);
			if (!PlaceFarmPayload.inRange(table.getPos(), payload.anchor(), player)) {
				TableNoticePayload.problem(player, Text.translatable("gui.the_dream_table.place_first"));
				return;
			}
			table.enqueue(player, new FarmSite(table.getPos(), payload.anchor(), facing, payload.mirrored()));
		});
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
