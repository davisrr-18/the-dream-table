package com.davisr.thedreamtable.farm;

import com.davisr.thedreamtable.TheDreamTableMod;
import com.davisr.thedreamtable.block.entity.TableSession;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
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
	public static boolean placeNext(TableSession table, ServerWorld world) {
		try {
			return step(table, world);
		} catch (RuntimeException exception) {
			TheDreamTableMod.LOGGER.error("Build step {} failed for {}", table.getBuildIndex(), table.getSelectedFarm(), exception);
			table.placementFailed(table.getPos());
			return false;
		}
	}

	/** The box of blocks this farm would occupy, used to keep two players' farms at one table apart. */
	public static Box footprint(TableSession table, FarmTemplate template) {
		Direction facing = facing(table);
		int[] low = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE};
		int[] high = {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
		for (FarmStep step : template.steps()) {
			BlockPos pos = target(table, template, step, facing);
			int[] at = {pos.getX(), pos.getY(), pos.getZ()};
			for (int axis = 0; axis < 3; axis++) {
				low[axis] = Math.min(low[axis], at[axis]);
				high[axis] = Math.max(high[axis], at[axis]);
			}
		}
		return new Box(low[0], low[1], low[2], high[0] + 1, high[1] + 1, high[2] + 1);
	}

	/** True when every step lies between the world floor and ceiling and inside the border. */
	public static boolean fits(ServerWorld world, TableSession table, FarmTemplate template) {
		Direction facing = facing(table);
		for (FarmStep step : template.steps()) {
			BlockPos pos = target(table, template, step, facing);
			if (!world.isInBuildLimit(pos) || !world.getWorldBorder().contains(pos)) {
				return false;
			}
		}
		return true;
	}

	private static boolean step(TableSession table, ServerWorld world) {
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
		BlockState existing = placed == null ? null : world.getBlockState(target);

		if (placed != null) {
			if (BlockMatch.sameBlock(existing, placed)) {
				return advance(world, table, template, facing);
			}
			if (!existing.isReplaceable() && !ObstacleRules.clearable(world, table.getPos(), target, existing)) {
				table.obstruct(target, existing);
				return false;
			}
		}
		if (!permitted(world, table, target, existing)) {
			table.protectedSpot(target);
			return false;
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

	/**
	 * Builds as the player who started it, so protected land stays protected. While that player is offline
	 * the area was already checked when the build started, and the build goes on.
	 */
	private static boolean permitted(ServerWorld world, TableSession table, BlockPos target, BlockState existing) {
		ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(table.getOwner());
		if (owner == null) {
			return true;
		}
		if (existing != null && !existing.isReplaceable()) {
			return BuildPermission.canBreak(world, owner, target, existing);
		}
		return BuildPermission.canPlace(world, owner, target);
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

	private static boolean advance(ServerWorld world, TableSession table, FarmTemplate template, Direction facing) {
		table.advance();
		if (table.getBuildIndex() >= template.steps().size()) {
			settle(world, table, template, facing);
			table.finish();
		}
		return true;
	}

	static Direction facing(TableSession table) {
		return table.hasAnchor() ? table.getFarmFacing() : table.getCachedState().get(Properties.HORIZONTAL_FACING);
	}

	static BlockPos target(TableSession table, FarmTemplate template, FarmStep step, Direction facing) {
		return table.hasAnchor()
				? FarmGeometry.toAimed(table.getFarmAnchor(), facing, template, step)
				: FarmGeometry.toWorld(table.getPos(), facing, step);
	}

	/** Blocks are placed without neighbor updates, so the finished farm gets one update pass to start its redstone and liquids. */
	private static void settle(ServerWorld world, TableSession table, FarmTemplate template, Direction facing) {
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
