package com.davisr.thedreamtable.farm;

import com.davisr.thedreamtable.block.entity.SessionBuild;
import com.davisr.thedreamtable.block.entity.TableSession;
import net.minecraft.block.BlockState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Clears a build's footprint one layer at a time, just before the layer's first step: every block of the layer that no step
 * places is removed without drops, unless its rules, another farm or the owner's permission keep it. A repair never clears.
 * {@link LayerClear} holds the cursor and the per-tick budget; this class does the world work.
 */
final class LayerClearer {
	private LayerClearer() {
	}

	/**
	 * Clears the layer of {@code target} unless it is already cleared. Returns null when the step may go on, or the outcome of
	 * this tick when clearing goes on: the tick's cells are spent, or a chunk of the layer is still loading.
	 */
	static @Nullable FarmBuilder.Outcome clearFirst(TableSession table, ServerWorld world, ServerPlayerEntity owner, FarmTemplate template,
			FarmSite site, BlockPos target) {
		SessionBuild build = table.build();
		LayerClear clear = build.layerClear(template, site);
		int layer = clear.layerOf(target);
		if (!clearDue(build.repairing(), build.layerCleared(layer))) {
			return null;
		}
		clear.start(layer);
		List<BlockPos> cleared = new ArrayList<>();
		List<BlockPos> liquids = new ArrayList<>();
		try {
			if (!clearCells(table, world, owner, build, clear, cleared, liquids)) {
				return FarmBuilder.Outcome.CLEARING;
			}
		} finally {
			// Neighbors are told once per tick for what was removed, so what lost its support drops and liquid flows out, as in a Take Down.
			FarmDismantler.drain(world, liquids);
			FarmDismantler.settle(world, cleared);
		}
		build.markLayerCleared(layer);
		clear.finish();
		return null;
	}

	/** A layer is cleared once, before its first step, and never during a repair, which keeps what the player already changed. */
	static boolean clearDue(boolean repairing, boolean layerCleared) {
		return !repairing && !layerCleared;
	}

	/** Looks at the layer's cells while this tick has budget. Returns false when it stopped before the layer was done. */
	private static boolean clearCells(TableSession table, ServerWorld world, ServerPlayerEntity owner, SessionBuild build, LayerClear clear,
			List<BlockPos> cleared, List<BlockPos> liquids) {
		for (BlockPos cell = clear.current(); cell != null; cell = clear.current()) {
			if (!clear.hasBudget(world.getTime())) {
				return false;
			}
			if (!world.isChunkLoaded(cell)) {
				ChunkKeeper.keepSpot(world, cell);
				build.waitingFor(world, cell);
				return false;
			}
			clearCell(table, world, owner, clear, cell, cleared, liquids);
			clear.advance();
		}
		return true;
	}

	/** Removes the cell's block, and its other half when it is a double block, only if both may go. */
	private static void clearCell(TableSession table, ServerWorld world, ServerPlayerEntity owner, LayerClear clear, BlockPos cell,
			List<BlockPos> cleared, List<BlockPos> liquids) {
		BlockState state = world.getBlockState(cell);
		BlockPos partner = ObstacleRules.partnerOf(world, cell, state);
		if (!mayClear(table, world, owner, clear, cell, state)) {
			return;
		}
		BlockState partnerState = partner == null ? null : world.getBlockState(partner);
		if (partner != null && !mayClear(table, world, owner, clear, partner, partnerState)) {
			return;
		}
		ObstacleRules.removeWithoutDrops(world, cell, state, partner);
		note(cell, state, cleared, liquids);
		if (partner != null) {
			note(partner, partnerState, cleared, liquids);
		}
	}

	/** The block's own rules, no other farm's area, and the owner's permission to break it. */
	private static boolean mayClear(TableSession table, ServerWorld world, ServerPlayerEntity owner, LayerClear clear, BlockPos pos, BlockState state) {
		return ObstacleRules.clearableInLayer(world, table.getPos(), pos, state, clear.isStepCell(pos))
				&& !FarmBuilder.insideOtherFarm(world, table, pos)
				&& BuildPermission.canBreak(world, owner, pos, state);
	}

	private static void note(BlockPos pos, BlockState removed, List<BlockPos> cleared, List<BlockPos> liquids) {
		cleared.add(pos);
		if (!removed.getFluidState().isEmpty()) {
			liquids.add(pos);
		}
	}
}
