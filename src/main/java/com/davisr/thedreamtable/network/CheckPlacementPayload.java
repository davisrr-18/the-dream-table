package com.davisr.thedreamtable.network;

import com.davisr.thedreamtable.TheDreamTableMod;
import com.davisr.thedreamtable.block.entity.DreamTableBlockEntity;
import com.davisr.thedreamtable.block.entity.TableSession;
import com.davisr.thedreamtable.farm.FarmGeometry;
import com.davisr.thedreamtable.farm.FarmSite;
import com.davisr.thedreamtable.platform.Platform;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Sent when the player anchors or turns the hologram, and every few seconds while it waits for Build.
 * The server runs the same checks Build will and answers with {@link PlacementResultPayload},
 * so the hologram turns red and the reason shows right away.
 */
public record CheckPlacementPayload(BlockPos table, BlockPos anchor, Direction facing, boolean announce) implements CustomPayload {
	public static final Id<CheckPlacementPayload> ID = new Id<>(Identifier.of(TheDreamTableMod.MOD_ID, "check_placement"));
	public static final PacketCodec<RegistryByteBuf, CheckPlacementPayload> CODEC = PacketCodec.tuple(
			BlockPos.PACKET_CODEC, CheckPlacementPayload::table,
			BlockPos.PACKET_CODEC, CheckPlacementPayload::anchor,
			Direction.PACKET_CODEC, CheckPlacementPayload::facing,
			PacketCodecs.BOOL, CheckPlacementPayload::announce,
			CheckPlacementPayload::new
	);

	/** The player may stand anywhere they can aim the hologram from. */
	private static final double MAX_DISTANCE = FarmGeometry.AIM_RANGE + 16.0;

	private static final RequestThrottle THROTTLE = new RequestThrottle();

	/**
	 * The client sends this from the world while aiming, usually with the menu closed, so no open menu is required:
	 * distance, a non-spectator player and a request budget guard it instead.
	 */
	public static void register() {
		THROTTLE.forgetOnDisconnect();
		Platform.network().registerClientToServer(ID, CODEC, (payload, player) -> {
			if (player.isSpectator() || !THROTTLE.allow(player.getUuid(), player.getServer().getTicks())) {
				return;
			}
			if (player.squaredDistanceTo(payload.table().toCenterPos()) > MAX_DISTANCE * MAX_DISTANCE || !(player.getWorld() instanceof ServerWorld world)) {
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
			FarmSite site = new FarmSite(table.getPos(), payload.anchor(), facing);
			String problem = table.problemAt(world, player, site);
			int existing = problem != null ? 0 : payload.announce() ? table.existingPercent(world, site) : PlacementResultPayload.UNCHANGED;
			Platform.network().send(player, new PlacementResultPayload(payload.table(), payload.anchor(), facing, problem == null ? "" : problem, existing));
			if (problem != null && payload.announce()) {
				player.sendMessage(Text.translatable(problem), true);
			}
		});
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
