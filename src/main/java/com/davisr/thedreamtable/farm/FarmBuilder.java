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

	/** What one call did: placed a block or spawned an entity, skipped a block already in place, or stopped. */
	public enum Outcome {
		PLACED, SKIPPED, STOPPED
	}

	/**
	 * An unexpected error pauses the build instead of crashing the server. Only the first block of a tick plays
	 * its place sound, since fast paces place many blocks per tick.
	 */
	public static Outcome placeNext(TableSession table, ServerWorld world, boolean sound) {
		try {
			return step(table, world, sound);
		} catch (RuntimeException exception) {
			TheDreamTableMod.LOGGER.error("Build step {} failed for {}", table.getBuildIndex(), table.getSelectedFarm(), exception);
			table.placementFailed(table.getPos());
			return Outcome.STOPPED;
		}
	}

	/** Where this session's farm sits: its anchor, or beside the table when it has none. */
	public static FarmSite site(TableSession table) {
		return new FarmSite(table.getPos(), table.hasAnchor() ? table.getFarmAnchor() : null, facing(table));
	}

	private static Outcome step(TableSession table, ServerWorld world, boolean sound) {
		FarmTemplate template = FarmTemplates.get(table.getSelectedFarm());
		if (template == null) {
			return Outcome.STOPPED;
		}
		if (table.getBuildIndex() >= template.steps().size()) {
			table.finish();
			return Outcome.STOPPED;
		}
		ServerPlayerEntity owner = table.onlineOwner(world);
		if (owner == null) {
			return Outcome.STOPPED;
		}
		FarmStep step = template.steps().get(table.getBuildIndex());
		FarmSite site = site(table);
		BlockPos target = site.target(template, step);
		if (!world.isChunkLoaded(target)) {
			return Outcome.STOPPED;
		}
		BlockState placed = step.isEntity() ? null : table.swapState(step.state()).rotate(FarmGeometry.rotation(site.facing()));
		BlockState existing = placed == null ? null : world.getBlockState(target);

		if (placed != null) {
			if (BlockMatch.sameBlock(existing, placed)) {
				advance(world, table, template, site);
				return Outcome.SKIPPED;
			}
			if (!existing.isReplaceable() && (!ObstacleRules.clearable(world, table.getPos(), target, existing) || insideOtherFarm(world, table, target))) {
				table.obstruct(target, existing);
				return Outcome.STOPPED;
			}
		}
		if (!permitted(world, owner, target, existing)) {
			table.protectedSpot(target);
			return Outcome.STOPPED;
		}
		boolean free = owner.isCreative();
		boolean costs = step.item() != null && !free;
		if (costs && !table.hasMaterial(step.item())) {
			table.missingMaterial();
			return Outcome.STOPPED;
		}

		boolean done = placed == null ? spawn(world, target, step) : place(world, target, placed, step, !free, sound);
		if (!done) {
			table.placementFailed(target);
			return Outcome.STOPPED;
		}
		boolean paid = costs && table.spendMaterial(step.item());
		if (placed != null) {
			table.recordPlaced(paid);
		}
		if (placed != null && step.item() == Items.WATER_BUCKET && paid) {
			FarmSpawns.returnBucket(world, table);
		}
		advance(world, table, template, site);
		return Outcome.PLACED;
	}

	/** Builds as the player who started it, so protected land stays protected. The build waits while that player is offline. */
	private static boolean permitted(ServerWorld world, ServerPlayerEntity owner, BlockPos target, BlockState existing) {
		if (existing != null && !existing.isReplaceable()) {
			return BuildPermission.canBreak(world, owner, target, existing);
		}
		return BuildPermission.canPlace(world, owner, target);
	}

	/** Blocks inside another table's farm, or another player's, belong to that farm and are never cleared. */
	private static boolean insideOtherFarm(ServerWorld world, TableSession table, BlockPos target) {
		return FarmAreas.of(world).reservedByOther(world, FarmAreas.Key.build(table.getPos(), table.getOwner()), target);
	}

	private static boolean spawn(ServerWorld world, BlockPos target, FarmStep step) {
		if (!FarmSpawns.spawn(world, target, step)) {
			return false;
		}
		world.playSound(null, target, SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.BLOCKS, 0.6F, 1.1F);
		return true;
	}

	/** Sets the block, confirms it took, then settles the block it replaced. Drops items only in survival. */
	private static boolean place(ServerWorld world, BlockPos target, BlockState placed, FarmStep step, boolean drop, boolean sound) {
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
		if (sound) {
			world.playSound(null, target, placed.getSoundGroup().getPlaceSound(), SoundCategory.BLOCKS, 0.85F, 0.9F + world.random.nextFloat() * 0.2F);
		}
		return true;
	}

	private static void advance(ServerWorld world, TableSession table, FarmTemplate template, FarmSite site) {
		table.advance();
		if (table.getBuildIndex() >= template.steps().size()) {
			settle(world, template, site);
			table.finish();
		}
	}

	private static Direction facing(TableSession table) {
		return table.hasAnchor() ? table.getFarmFacing() : table.getCachedState().get(Properties.HORIZONTAL_FACING);
	}

	/** Blocks are placed without neighbor updates, so the finished farm gets one update pass to start its redstone and liquids. */
	private static void settle(ServerWorld world, FarmTemplate template, FarmSite site) {
		for (FarmStep step : template.steps()) {
			if (step.isEntity() || step.state() == null) {
				continue;
			}
			BlockPos pos = site.target(template, step);
			BlockState state = world.getBlockState(pos);
			if (!state.isAir()) {
				world.updateNeighbors(pos, state.getBlock());
			}
		}
	}
}
