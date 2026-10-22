package com.davisr.thedreamtable.farm;

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
 * Places one build step per call. Every check runs before anything changes, so a step that stops
 * (blocked spot, missing material) leaves the world and the counts untouched and is retried as is.
 */
public final class FarmBuilder {
	private FarmBuilder() {
	}

	public static boolean placeNext(DreamTableBlockEntity table, ServerWorld world) {
		FarmTemplate template = FarmTemplates.get(table.getSelectedFarm());
		if (template == null || table.getBuildIndex() >= template.steps().size()) {
			table.finish();
			return false;
		}
		FarmStep step = template.steps().get(table.getBuildIndex());
		Direction facing = facing(table);
		BlockPos target = target(table, template, step, facing);
		BlockState placed = step.isEntity() ? null : table.swapState(step.state()).rotate(FarmGeometry.rotation(facing));

		if (placed != null) {
			BlockState existing = world.getBlockState(target);
			if (alreadyThere(existing, placed)) {
				return advance(world, table, template, facing);
			}
			if (!existing.isReplaceable() && !ObstacleRules.clearable(world, table.getPos(), target, existing)) {
				table.obstruct(target, existing);
				return false;
			}
		}
		boolean free = table.creativeOwner(world);
		if (step.item() != null && !free) {
			if (!table.hasMaterial(step.item())) {
				table.missingMaterial();
				return false;
			}
			table.consumeMaterial(step.item());
		}

		if (placed == null) {
			FarmSpawns.spawn(world, target, step);
			world.playSound(null, target, SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.BLOCKS, 0.6F, 1.1F);
		} else {
			place(world, target, placed, step, !free);
			table.recordPlaced(step.item() != null && !free);
			if (step.item() == Items.WATER_BUCKET && !free) {
				FarmSpawns.returnBucket(world, table);
			}
		}
		return advance(world, table, template, facing);
	}

	/** Drops the old block's items only in survival; in creative the obstacle is just removed. */
	private static void place(ServerWorld world, BlockPos target, BlockState placed, FarmStep step, boolean drop) {
		ObstacleRules.clear(world, target, world.getBlockState(target), drop);
		world.setBlockState(target, placed, Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
		BlockEntity blockEntity = world.getBlockEntity(target);
		if (step.blockNbt() != null && blockEntity != null) {
			NbtCompound data = step.blockNbt().copy();
			data.putInt("x", target.getX());
			data.putInt("y", target.getY());
			data.putInt("z", target.getZ());
			blockEntity.read(data, world.getRegistryManager());
		}
		world.playSound(null, target, placed.getSoundGroup().getPlaceSound(), SoundCategory.BLOCKS, 0.85F, 0.9F + world.random.nextFloat() * 0.2F);
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

	/** A spot counts as built when it holds the same block; liquids must also match their level. */
	private static boolean alreadyThere(BlockState existing, BlockState placed) {
		if (!existing.isOf(placed.getBlock())) {
			return false;
		}
		if (placed.contains(Properties.LEVEL_15) && existing.contains(Properties.LEVEL_15)) {
			return existing.get(Properties.LEVEL_15).equals(placed.get(Properties.LEVEL_15));
		}
		return true;
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
