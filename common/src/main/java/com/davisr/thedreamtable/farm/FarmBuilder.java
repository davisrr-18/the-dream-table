package com.davisr.thedreamtable.farm;

import com.davisr.thedreamtable.TheDreamTableMod;
import com.davisr.thedreamtable.block.DreamTableBlock;
import com.davisr.thedreamtable.block.entity.BuildIssue;
import com.davisr.thedreamtable.block.entity.RepairContext;
import com.davisr.thedreamtable.block.entity.TableSession;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * Places one build step per call, in this order: check everything, place, confirm the block is there,
 * and only then spend the material, record the step and advance. A step that stops at any point leaves the
 * counts untouched and is retried as is, so a retry never spends twice or drops an obstacle twice.
 * Every stop carries a {@link BuildIssue} saying why. A repair leaves alone the blocks the player changed, and no build
 * spawns a mob or minecart that is already in its spot.
 */
public final class FarmBuilder {
	private static final int FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;

	private FarmBuilder() {
	}

	/**
	 * What one call did: placed a block or spawned an entity, skipped a block already in place, or stopped. CLEARING means the
	 * layer of the next step is still being cleared (its cells for this tick are spent, or a chunk is loading); the step waits for a later tick.
	 */
	public enum Outcome {
		PLACED, SKIPPED, STOPPED, CLEARING
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
			table.build().failed(table.getPos());
			return Outcome.STOPPED;
		}
	}

	/**
	 * Why the next block can't be placed now, or null when nothing the player must fix is in the way. Used before
	 * resuming, so a build stopped by a protected spot, a block the table won't clear or a missing item doesn't
	 * start again only to stop at once.
	 */
	public static @Nullable BuildIssue check(TableSession table, ServerWorld world) {
		FarmTemplate template = FarmTemplates.get(table.getSelectedFarm());
		ServerPlayerEntity owner = table.onlineOwner(world);
		int index = table.getBuildIndex();
		if (template == null || owner == null || index >= template.steps().size() || FarmGrowth.isGrowth(template, index)) {
			return null;
		}
		FarmStep step = template.steps().get(index);
		FarmSite site = site(table);
		BlockPos target = site.target(template, step);
		if (!world.isChunkLoaded(target)) {
			return null;
		}
		BlockState placed = placedState(table, site, step);
		BlockState existing = placed == null ? null : world.getBlockState(target);
		if (placed == null) {
			FarmSpawns.Result entityStatus = FarmSpawns.status(world, target, step);
			if (entityStatus == FarmSpawns.Result.BLOCKED) {
				return BuildIssue.at(BuildIssue.Kind.ENTITY_BLOCKED, target);
			}
			if (entityStatus == FarmSpawns.Result.PRESENT) {
				return null;
			}
		}
		if (placed != null && (BlockMatch.sameBlock(existing, placed) || (table.build().repairing() && !existing.isReplaceable()))) {
			return null;
		}
		return blocker(table, world, owner, step, target, placed, existing);
	}

	/** The cause that stops this step, checked in the order the build meets them, or null. */
	private static @Nullable BuildIssue blocker(TableSession table, ServerWorld world, ServerPlayerEntity owner, FarmStep step, BlockPos target,
			@Nullable BlockState placed, @Nullable BlockState existing) {
		if (placed != null && !existing.isReplaceable()) {
			if (insideOtherFarm(world, table, target)) {
				return BuildIssue.of(BuildIssue.Kind.OTHER_FARM, target, existing.getBlock());
			}
			if (!ObstacleRules.clearable(world, table.getPos(), target, existing)) {
				return existing.getBlock() instanceof DreamTableBlock
						? BuildIssue.at(BuildIssue.Kind.TABLE_IN_WAY, target)
						: BuildIssue.of(BuildIssue.Kind.OBSTACLE, target, existing.getBlock());
			}
		}
		if (!permitted(world, owner, target, existing, placed)) {
			return BuildIssue.at(BuildIssue.Kind.PROTECTED, target);
		}
		if (!supplied(table, world, owner, step)) {
			return isWater(step) ? BuildIssue.at(BuildIssue.Kind.WATER, target)
					: BuildIssue.of(BuildIssue.Kind.MATERIAL, target, table.resolve(step.item()));
		}
		return null;
	}

	/** Where a step's item comes from: nothing (no item, Creative, or water with an infinite source near) or the delivered counts. */
	enum Supply {
		FREE, COUNTS
	}

	/** A water step with an infinite source near is free: it spends no delivered bucket and hands none back. Otherwise it uses delivered buckets like any item. */
	static Supply supply(FarmStep step, boolean creative, boolean infiniteWater) {
		if (step.item() == null || creative || (infiniteWater && isWater(step))) {
			return Supply.FREE;
		}
		return Supply.COUNTS;
	}

	/** True for a water step: its block is water and its item is the bucket the schematic reader recorded for it. */
	public static boolean isWater(FarmStep step) {
		return step.item() == Items.WATER_BUCKET;
	}

	private static boolean supplied(TableSession table, ServerWorld world, ServerPlayerEntity owner, FarmStep step) {
		return switch (supply(step, owner.isCreative(), isWater(step) && table.hasInfiniteWater(world))) {
			case FREE -> true;
			case COUNTS -> table.hasMaterial(step.item());
		};
	}

	private static @Nullable BlockState placedState(TableSession table, FarmSite site, FarmStep step) {
		return step.isEntity() ? null : site.orient(table.swapState(step.state()));
	}

	/** Where this session's farm sits: its anchor, or beside the table when it has none. */
	public static FarmSite site(TableSession table) {
		return new FarmSite(table.getPos(), table.hasAnchor() ? table.getFarmAnchor() : null, facing(table), table.hasAnchor() && table.getFarmMirrored());
	}

	private static Outcome step(TableSession table, ServerWorld world, boolean sound) {
		FarmTemplate template = FarmTemplates.get(table.getSelectedFarm());
		if (template == null) {
			return Outcome.STOPPED;
		}
		ServerPlayerEntity owner = table.onlineOwner(world);
		if (table.getBuildIndex() >= template.steps().size()) {
			// The layers above the last step are cleared before the build counts as done.
			FarmSite site = site(table);
			Outcome clearing = owner == null ? null : LayerClearer.clearRest(table, world, owner, template, site);
			if (clearing != null) {
				return clearing;
			}
			if (LayerClearer.layersLeft(table, template, site)) {
				return Outcome.STOPPED;
			}
			settle(world, template, site);
			table.build().finish();
			return Outcome.STOPPED;
		}
		if (owner == null) {
			return Outcome.STOPPED;
		}
		FarmStep step = template.steps().get(table.getBuildIndex());
		if (FarmGrowth.isGrowth(template, table.getBuildIndex())) {
			advance(world, table, template, site(table));
			return Outcome.SKIPPED;
		}
		FarmSite site = site(table);
		BlockPos target = site.target(template, step);
		ChunkKeeper.keepSpot(world, target);
		if (!world.isChunkLoaded(target)) {
			table.build().waitingFor(world, target);
			return Outcome.STOPPED;
		}
		// Clearing destroys without drops, so it waits until the step can go on: a build short of material clears nothing. A step
		// whose block already stands goes on too, so every step that counts as placed sits in a cleared layer.
		BlockState wanted = placedState(table, site, step);
		// The block already standing is checked first: a skipped water step must not scan for a source.
		boolean affordable = (wanted != null && BlockMatch.sameBlock(world.getBlockState(target), wanted))
				|| supplied(table, world, owner, step);
		Outcome clearing = affordable ? LayerClearer.clearFirst(table, world, owner, template, site, target) : null;
		if (clearing != null) {
			return clearing;
		}
		BlockState placed = placedState(table, site, step);
		BlockState existing = placed == null ? null : world.getBlockState(target);

		FarmSpawns.Result entityStatus = placed == null ? FarmSpawns.status(world, target, step) : null;
		if (entityStatus == FarmSpawns.Result.PRESENT || (placed != null && BlockMatch.sameBlock(existing, placed))) {
			advance(world, table, template, site);
			return Outcome.SKIPPED;
		}
		if (entityStatus == FarmSpawns.Result.BLOCKED) {
			table.build().stopFor(BuildIssue.at(BuildIssue.Kind.ENTITY_BLOCKED, target));
			return Outcome.STOPPED;
		}
		if (placed != null && table.build().repairing() && !existing.isReplaceable()) {
			if (!BlockMatch.stillFarm(existing, placed)) {
				table.build().keepChanged();
			}
			advance(world, table, template, site);
			return Outcome.SKIPPED;
		}
		BuildIssue issue = blocker(table, world, owner, step, target, placed, existing);
		if (issue != null) {
			table.build().stopFor(issue);
			return Outcome.STOPPED;
		}
		boolean free = owner.isCreative();
		boolean costs = supply(step, free, isWater(step) && table.hasInfiniteWater(world)) == Supply.COUNTS;

		FarmSpawns.Result spawnResult = placed == null ? spawn(world, target, step) : null;
		boolean done = placed == null ? spawnResult == FarmSpawns.Result.PLACED : place(world, owner, target, placed, step, !free, sound);
		if (spawnResult == FarmSpawns.Result.PRESENT) {
			advance(world, table, template, site);
			return Outcome.SKIPPED;
		}
		if (spawnResult == FarmSpawns.Result.BLOCKED) {
			table.build().stopFor(BuildIssue.at(BuildIssue.Kind.ENTITY_BLOCKED, target));
			return Outcome.STOPPED;
		}
		if (!done) {
			if (placed == null) {
				table.build().failed(target);
			} else {
				table.build().placementFailed(target, placed);
			}
			return Outcome.STOPPED;
		}
		boolean paid = costs && table.spendMaterial(step.item());
		if (placed != null) {
			table.build().recordPlaced(FarmSpawns.recordsAsPaid(step.item(), paid));
		}
		// Only a paid step hands back its bucket; a water step covered by a source is not paid, so no empty bucket is returned for it.
		if (placed != null && step.item() != null && FarmSpawns.leavesEmptyBucket(step.item()) && paid) {
			FarmSpawns.returnBucket(world, table);
		}
		advance(world, table, template, site);
		return Outcome.PLACED;
	}

	/** Builds as the player who started it, so protected land stays protected. The build waits while that player is offline. */
	private static boolean permitted(ServerWorld world, ServerPlayerEntity owner, BlockPos target, @Nullable BlockState existing, @Nullable BlockState placed) {
		if (placed == null) {
			return BuildPermission.canPlace(world, owner, target);
		}
		if (!existing.isReplaceable() && !BuildPermission.canBreak(world, owner, target, existing)) {
			return false;
		}
		return BuildPermission.canPlace(world, owner, target, placed);
	}

	/** Blocks inside another table's farm, or another player's, belong to that farm and are never cleared. */
	static boolean insideOtherFarm(ServerWorld world, TableSession table, BlockPos target) {
		RepairContext repair = table.build().repairContext();
		return FarmAreas.of(world).reservedByOther(world, FarmAreas.Key.build(table.getPos(), table.getOwner()), target,
				repair == null ? null : repair.farmKey().instanceId());
	}

	private static FarmSpawns.Result spawn(ServerWorld world, BlockPos target, FarmStep step) {
		FarmSpawns.Result result = FarmSpawns.spawn(world, target, step);
		if (result != FarmSpawns.Result.PLACED) {
			return result;
		}
		world.playSound(null, target, SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.BLOCKS, 0.6F, 1.1F);
		return result;
	}

	/**
	 * Sets the block and confirms it took; false means nothing was placed. Once the block stands the step counts as placed,
	 * so a failure in the work that follows is logged and neither undoes nor repeats it: a retry would find the block
	 * already there and skip it unpaid and untracked. Drops items only in survival.
	 */
	private static boolean place(ServerWorld world, ServerPlayerEntity owner, BlockPos target, BlockState placed, FarmStep step, boolean drop, boolean sound) {
		BlockState old = world.getBlockState(target);
		if (!world.setBlockState(target, placed, FLAGS) || !world.getBlockState(target).isOf(placed.getBlock())) {
			return false;
		}
		try {
			settlePlaced(world, owner, target, placed, step, old, drop, sound);
		} catch (RuntimeException exception) {
			TheDreamTableMod.LOGGER.error("Finishing the block at {} failed after it was placed; the step still counts as placed and paid", target, exception);
		}
		return true;
	}

	/** Settles the block that was replaced, loads the placed block's saved data, and plays the place effects. */
	private static void settlePlaced(ServerWorld world, ServerPlayerEntity owner, BlockPos target, BlockState placed, FarmStep step, BlockState old, boolean drop,
			boolean sound) {
		ObstacleRules.afterReplace(world, owner, target, old, drop);
		BlockEntity blockEntity = world.getBlockEntity(target);
		if (step.blockNbt() != null && blockEntity != null) {
			NbtCompound data = step.blockNbt().copy();
			data.putInt("x", target.getX());
			data.putInt("y", target.getY());
			data.putInt("z", target.getZ());
			blockEntity.readNbt(data);
		}
		if (sound) {
			world.playSound(null, target, placed.getSoundGroup().getPlaceSound(), SoundCategory.BLOCKS, 0.85F, 0.9F + world.random.nextFloat() * 0.2F);
			world.spawnParticles(ParticleTypes.END_ROD, target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0.01);
		}
	}

	private static void advance(ServerWorld world, TableSession table, FarmTemplate template, FarmSite site) {
		table.build().advance();
		// With layers still to clear above the last step, the next call clears them and then finishes.
		if (table.getBuildIndex() >= template.steps().size() && !LayerClearer.layersLeft(table, template, site)) {
			settle(world, template, site);
			table.build().finish();
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
