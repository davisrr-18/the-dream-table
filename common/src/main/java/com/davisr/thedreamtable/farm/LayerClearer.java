package com.davisr.thedreamtable.farm;

import com.davisr.thedreamtable.block.entity.SessionBuild;
import com.davisr.thedreamtable.block.entity.TableSession;
import net.minecraft.block.BlockState;
import net.minecraft.block.FallingBlock;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * Clears a build's footprint one layer at a time, from the bottom, before the first step of a layer above: every block of the
 * layer that no step places is removed without drops, unless its rules, another farm or the owner's permission keep it. Layers
 * left above the last step are cleared once the steps are done. A repair never clears.
 * {@link LayerClear} holds the cursor and the per-tick budget; this class does the world work.
 */
final class LayerClearer {
	private LayerClearer() {
	}

	/**
	 * Clears every layer up to the one of {@code target} that is not cleared yet, from the bottom. Returns null when the step may
	 * go on, or the outcome of this tick when clearing goes on: the tick's cells are spent, or a chunk of the layer is still loading.
	 */
	static @Nullable FarmBuilder.Outcome clearFirst(TableSession table, ServerWorld world, ServerPlayerEntity owner, FarmTemplate template,
			FarmSite site, BlockPos target) {
		LayerClear clear = table.build().layerClear(template, site);
		return clearThrough(table, world, owner, clear, clear.layerOf(target));
	}

	/**
	 * Clears the layers left above the last step, once every step is done: a top layer holding only growth steps, or none at
	 * all, is cleared too, so the whole footprint ends clear. Same returns as {@link #clearFirst}.
	 */
	static @Nullable FarmBuilder.Outcome clearRest(TableSession table, ServerWorld world, ServerPlayerEntity owner, FarmTemplate template,
			FarmSite site) {
		LayerClear clear = table.build().layerClear(template, site);
		return clearThrough(table, world, owner, clear, clear.topLayer());
	}

	/** Whether layers of the footprint are still to be cleared before the build can finish. */
	static boolean layersLeft(TableSession table, FarmTemplate template, FarmSite site) {
		SessionBuild build = table.build();
		return layerDue(build.repairing(), build::layerCleared, build.layerClear(template, site).topLayer()) >= 0;
	}

	private static @Nullable FarmBuilder.Outcome clearThrough(TableSession table, ServerWorld world, ServerPlayerEntity owner,
			LayerClear clear, int through) {
		SessionBuild build = table.build();
		for (int layer = layerDue(build.repairing(), build::layerCleared, through); layer >= 0;
				layer = layerDue(build.repairing(), build::layerCleared, through)) {
			FarmBuilder.Outcome outcome = clearLayer(table, world, owner, build, clear, layer);
			if (outcome != null) {
				return outcome;
			}
		}
		return null;
	}

	/** Clears one layer, or as much of it as this tick allows. Null once the layer is done. */
	private static @Nullable FarmBuilder.Outcome clearLayer(TableSession table, ServerWorld world, ServerPlayerEntity owner, SessionBuild build,
			LayerClear clear, int layer) {
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

	/**
	 * The next layer to clear up to {@code through}, or -1 for none. Each layer is cleared once, and never during a repair, which
	 * keeps what the player already changed.
	 */
	static int layerDue(boolean repairing, IntPredicate cleared, int through) {
		return repairing ? -1 : LayerClear.nextUncleared(cleared, through);
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
		if (!mayClear(table, world, owner, cell, state, clear.isStepCell(cell))) {
			return;
		}
		BlockState partnerState = partner == null ? null : world.getBlockState(partner);
		if (partner != null && !mayClear(table, world, owner, partner, partnerState, clear.isStepCell(partner))) {
			return;
		}
		ObstacleRules.removeWithoutDrops(world, cell, state, partner);
		note(cell, state, cleared, liquids);
		if (partner != null) {
			note(partner, partnerState, cleared, liquids);
		}
		clearFallingAbove(table, world, owner, table.build(), clear, cell, cleared, liquids);
	}

	/**
	 * Sand, gravel and other falling blocks stacked above a cleared cell inside the footprint would fall into the layer just cleared
	 * and stay there, so the column of them goes too. A step cell counts here only in a layer not cleared yet: no step of such a
	 * layer has been placed, since a layer is always cleared before its steps, so what stands there is still the old ground and
	 * the step places its own block later. It stops at the first block that does not fall or may not be cleared.
	 */
	private static void clearFallingAbove(TableSession table, ServerWorld world, ServerPlayerEntity owner, SessionBuild build, LayerClear clear,
			BlockPos cell, List<BlockPos> cleared, List<BlockPos> liquids) {
		for (BlockPos above = cell.up(); clear.contains(above); above = above.up()) {
			BlockState state = world.getBlockState(above);
			boolean stepCell = clear.isStepCell(above) && build.layerCleared(clear.layerOf(above));
			if (!(state.getBlock() instanceof FallingBlock) || !mayClear(table, world, owner, above, state, stepCell)) {
				return;
			}
			ObstacleRules.removeWithoutDrops(world, above, state, null);
			note(above, state, cleared, liquids);
		}
	}

	/** The block's own rules, no other farm's area, and the owner's permission to break it. */
	private static boolean mayClear(TableSession table, ServerWorld world, ServerPlayerEntity owner, BlockPos pos, BlockState state, boolean stepCell) {
		return ObstacleRules.clearableInLayer(world, table.getPos(), pos, state, stepCell)
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
