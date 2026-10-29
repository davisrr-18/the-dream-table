package com.davisr.thedreamtable.network;

import com.davisr.thedreamtable.network.TableNoticePayload;
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
public record CheckPlacementPayload(BlockPos table, BlockPos anchor, Direction facing, boolean mirrored, boolean announce) implements CustomPayload {
	public static final Id<CheckPlacementPayload> ID = new Id<>(Identifier.of(TheDreamTableMod.MOD_ID, "check_placement"));
	public static final PacketCodec<RegistryByteBuf, CheckPlacementPayload> CODEC = PacketCodec.tuple(
			BlockPos.PACKET_CODEC, CheckPlacementPayload::table,
			BlockPos.PACKET_CODEC, CheckPlacementPayload::anchor,
			Direction.PACKET_CODEC, CheckPlacementPayload::facing,
			PacketCodecs.BOOL, CheckPlacementPayload::mirrored,
			PacketCodecs.BOOL, CheckPlacementPayload::announce,
			CheckPlacementPayload::new
	);

	/** The player may stand anywhere they can aim the hologram from. */
	private static final double MAX_DISTANCE = FarmGeometry.AIM_RANGE + 16.0;

	/**
	 * Each accepted check walks the whole template, so only a few scans a second are allowed per player. The client asks
	 * on a change of the placement and every two seconds, and a missed answer is asked again by that recheck.
	 */
	static final int BUDGET = 4;
	/** The same question asked again within a second gets the same answer without a new scan. */
	static final int CACHE_TICKS = 20;
	private static final RequestThrottle THROTTLE = new RequestThrottle(BUDGET, RequestThrottle.DEFAULT_WINDOW_TICKS);
	private static final RecentResults<Question, PlacementResultPayload> RECENT = new RecentResults<>(CACHE_TICKS);

	/** Everything the answer depends on besides the world, which the short time to live covers. */
	record Question(BlockPos table, BlockPos anchor, Direction facing, boolean mirrored, boolean announce, String farm) {
	}

	/**
	 * The client sends this from the world while aiming, usually with the menu closed, so no open menu is required:
	 * distance, a non-spectator player, a repeat cache and a request budget guard it instead.
	 */
	public static void register() {
		THROTTLE.forgetOnDisconnect();
		RECENT.forgetOnDisconnect();
		Platform.network().registerClientToServer(ID, CODEC, (payload, player) -> {
			if (player.isSpectator()) {
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
			long now = player.getServer().getTicks();
			Question question = new Question(payload.table(), payload.anchor(), facing, payload.mirrored(), payload.announce(), table.getSelectedFarm());
			PlacementResultPayload result = RECENT.get(player.getUuid(), question, now);
			if (result == null) {
				if (!THROTTLE.allow(player.getUuid(), now)) {
					return;
				}
				FarmSite site = new FarmSite(table.getPos(), payload.anchor(), facing, payload.mirrored());
				String problem = table.problemAt(world, player, site);
				int existing = problem != null ? 0 : payload.announce() ? table.existingPercent(world, site) : PlacementResultPayload.UNCHANGED;
				result = new PlacementResultPayload(payload.table(), payload.anchor(), facing, site.mirrored(), problem == null ? "" : problem, existing);
				RECENT.put(player.getUuid(), question, result, now);
			}
			Platform.network().send(player, result);
			if (!result.problem().isEmpty() && payload.announce()) {
				TableNoticePayload.problem(player, Text.translatable(result.problem()));
			}
		});
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
