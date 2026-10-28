package com.davisr.thedreamtable.farm;

import com.davisr.thedreamtable.TheDreamTableMod;
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
				return BuildIssue.of(BuildIssue.Kind.OBSTACLE, target, existing.getBlock());
			}
		}
		if (!permitted(world, owner, target, existing)) {
			return BuildIssue.at(BuildIssue.Kind.PROTECTED, target);
		}
		if (step.item() != null && !owner.isCreative() && !table.hasMaterial(step.item())) {
			return BuildIssue.of(BuildIssue.Kind.MATERIAL, target, table.resolve(step.item()));
		}
		return null;
	}

	private static @Nullable BlockState placedState(TableSession table, FarmSite site, FarmStep step) {
		return step.isEntity() ? null : table.swapState(step.state()).rotate(FarmGeometry.rotation(site.facing()));
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
			table.build().finish();
			return Outcome.STOPPED;
		}
		ServerPlayerEntity owner = table.onlineOwner(world);
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
		boolean costs = step.item() != null && !free;

		FarmSpawns.Result spawnResult = placed == null ? spawn(world, target, step) : null;
		boolean done = placed == null ? spawnResult == FarmSpawns.Result.PLACED : place(world, target, placed, step, !free, sound);
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
			table.build().recordPlaced(paid);
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
			world.spawnParticles(ParticleTypes.END_ROD, target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0.01);
		}
		return true;
	}

	private static void advance(ServerWorld world, TableSession table, FarmTemplate template, FarmSite site) {
		table.build().advance();
		if (table.getBuildIndex() >= template.steps().size()) {
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
