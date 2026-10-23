package com.davisr.thedreamtable.network;

import com.davisr.thedreamtable.TheDreamTableMod;
import com.davisr.thedreamtable.block.entity.DreamTableBlockEntity;
import com.davisr.thedreamtable.block.entity.TableSession;
import com.davisr.thedreamtable.farm.FarmSite;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Sent when the player anchors or turns the hologram, before Build. The server runs the same checks Build will
 * and answers with {@link PlacementResultPayload}, so the hologram turns red and the reason shows right away.
 */
public record CheckPlacementPayload(BlockPos table, BlockPos anchor, Direction facing) implements CustomPayload {
	public static final Id<CheckPlacementPayload> ID = new Id<>(Identifier.of(TheDreamTableMod.MOD_ID, "check_placement"));
	public static final PacketCodec<RegistryByteBuf, CheckPlacementPayload> CODEC = PacketCodec.tuple(
			BlockPos.PACKET_CODEC, CheckPlacementPayload::table,
			BlockPos.PACKET_CODEC, CheckPlacementPayload::anchor,
			Direction.PACKET_CODEC, CheckPlacementPayload::facing,
			CheckPlacementPayload::new
	);

	public static void register() {
		PayloadTypeRegistry.playC2S().register(ID, CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ID, (payload, context) -> context.server().execute(() -> {
			ServerPlayerEntity player = context.player();
			if (player.squaredDistanceTo(payload.table().toCenterPos()) > 64.0 || !(player.getWorld() instanceof ServerWorld world)) {
				return;
			}
			if (!(world.getBlockEntity(payload.table()) instanceof DreamTableBlockEntity blockEntity)) {
				return;
			}
			TableSession table = blockEntity.session(player);
			if (!DreamTableBlockEntity.PHASE_IDLE.equals(table.getPhase()) || table.getSelectedFarm().isEmpty()
					|| !PlaceFarmPayload.inRange(table.getPos(), payload.anchor(), player)) {
				return;
			}
			Direction facing = PlaceFarmPayload.horizontal(payload.facing(), player);
			String problem = table.problemAt(world, player, new FarmSite(table.getPos(), payload.anchor(), facing));
			ServerPlayNetworking.send(player, new PlacementResultPayload(payload.table(), payload.anchor(), facing, problem == null ? "" : problem));
			if (problem != null) {
				player.sendMessage(Text.translatable(problem), true);
			}
		}));
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
