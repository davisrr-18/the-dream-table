package com.davisr.thedreamtable.farm;

import com.davisr.thedreamtable.TheDreamTableMod;
import com.davisr.thedreamtable.block.entity.DreamTableBlockEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Places one build step per call, in this order: check everything, place, confirm the block is there,
 * and only then spend the material, record the step and advance. A step that stops at any point leaves the
 * counts untouched and is retried as is, so a retry never spends twice or drops an obstacle twice.
 */
public final class FarmBuilder {
	private static final int FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;

	private FarmBuilder() {
	}

	/** An unexpected error pauses the build instead of crashing the server. */
	public static boolean placeNext(DreamTableBlockEntity table, ServerWorld world) {
		try {
			return step(table, world);
		} catch (RuntimeException exception) {
			TheDreamTableMod.LOGGER.error("Build step {} failed for {}", table.getBuildIndex(), table.getSelectedFarm(), exception);
			table.placementFailed(table.getPos());
			return false;
		}
	}

	/** True when every step lies between the world floor and ceiling and inside the border. */
	public static boolean fits(ServerWorld world, DreamTableBlockEntity table, FarmTemplate template) {
		Direction facing = facing(table);
		for (FarmStep step : template.steps()) {
			BlockPos pos = target(table, template, step, facing);
			if (!world.isInBuildLimit(pos) || !world.getWorldBorder().contains(pos)) {
				return false;
			}
		}
		return true;
	}

	private static boolean step(DreamTableBlockEntity table, ServerWorld world) {
		FarmTemplate template = FarmTemplates.get(table.getSelectedFarm());
		if (template == null || table.getBuildIndex() >= template.steps().size()) {
			table.finish();
			return false;
		}
		FarmStep step = template.steps().get(table.getBuildIndex());
		Direction facing = facing(table);
		BlockPos target = target(table, template, step, facing);
		if (!world.isChunkLoaded(target)) {
			return false;
		}
		BlockState placed = step.isEntity() ? null : table.swapState(step.state()).rotate(FarmGeometry.rotation(facing));

		if (placed != null) {
			BlockState existing = world.getBlockState(target);
			if (BlockMatch.sameBlock(existing, placed)) {
				return advance(world, table, template, facing);
			}
			if (!existing.isReplaceable() && !ObstacleRules.clearable(world, table.getPos(), target, existing)) {
				table.obstruct(target, existing);
				return false;
			}
		}
		boolean free = table.creativeOwner(world);
		boolean costs = step.item() != null && !free;
		if (costs && !table.hasMaterial(step.item())) {
			table.missingMaterial();
			return false;
		}

		boolean done = placed == null ? spawn(world, target, step) : place(world, target, placed, step, !free);
		if (!done) {
			table.placementFailed(target);
			return false;
		}
		if (placed != null) {
			table.recordPlaced(costs);
		}
		if (costs) {
			table.consumeMaterial(step.item());
		}
		if (placed != null && step.item() == Items.WATER_BUCKET && costs) {
			FarmSpawns.returnBucket(world, table);
		}
		return advance(world, table, template, facing);
	}

	private static boolean spawn(ServerWorld world, BlockPos target, FarmStep step) {
		if (!FarmSpawns.spawn(world, target, step)) {
			return false;
		}
		world.playSound(null, target, SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.BLOCKS, 0.6F, 1.1F);
		return true;
	}

	/** Sets the block, confirms it took, then settles the block it replaced. Drops items only in survival. */
	private static boolean place(ServerWorld world, BlockPos target, BlockState placed, FarmStep step, boolean drop) {
		BlockState old = world.getBlockState(target);
		if (!world.setBlockState(target, placed, FLAGS) || !world.getBlockState(target).isOf(placed.getBlock())) {
			return false;
		}
		ObstacleRules.afterReplace(world, target, old, drop);
		BlockEntity blockEntity = world.getBlockEntity(target);
		if (step.blockNbt() != null && blockEntity != null) {
			NbtCompound data = step.blockNbt().copy();
			data.putInt("x", target.getX());
			data.putInt("y", target.getY());
			data.putInt("z", target.getZ());
			blockEntity.read(data, world.getRegistryManager());
		}
		world.playSound(null, target, placed.getSoundGroup().getPlaceSound(), SoundCategory.BLOCKS, 0.85F, 0.9F + world.random.nextFloat() * 0.2F);
		return true;
	}

	private static boolean advance(ServerWorld world, DreamTableBlockEntity table, FarmTemplate template, Direction facing) {
		table.advance();
		if (table.getBuildIndex() >= template.steps().size()) {
			settle(world, table, template, facing);
			table.finish();
		}
		return true;
	}

	static Direction facing(DreamTableBlockEntity table) {
		return table.hasAnchor() ? table.getFarmFacing() : table.getCachedState().get(Properties.HORIZONTAL_FACING);
	}

	static BlockPos target(DreamTableBlockEntity table, FarmTemplate template, FarmStep step, Direction facing) {
		return table.hasAnchor()
				? FarmGeometry.toAimed(table.getFarmAnchor(), facing, template, step)
				: FarmGeometry.toWorld(table.getPos(), facing, step);
	}

	/** Blocks are placed without neighbor updates, so the finished farm gets one update pass to start its redstone and liquids. */
	private static void settle(ServerWorld world, DreamTableBlockEntity table, FarmTemplate template, Direction facing) {
		for (FarmStep step : template.steps()) {
			if (step.isEntity() || step.state() == null) {
				continue;
			}
			BlockPos pos = target(table, template, step, facing);
			BlockState state = world.getBlockState(pos);
			if (!state.isAir()) {
				world.updateNeighbors(pos, state.getBlock());
			}
		}
	}
}
