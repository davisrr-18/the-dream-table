package com.davisr.thedreamtable.farm;

import com.davisr.thedreamtable.TheDreamTableMod;
import com.davisr.thedreamtable.block.DreamTableBlock;
import net.minecraft.block.AbstractRailBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.InfestedBlock;
import net.minecraft.block.PistonBlock;
import net.minecraft.block.PistonHeadBlock;
import net.minecraft.block.TntBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.WorldEvents;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/** What the builder may remove from a farm spot, and how it removes it. */
final class ObstacleRules {
	private static final int FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;
	private static final Set<Block> KEEP = Set.of(
			Blocks.BUDDING_AMETHYST, Blocks.REINFORCED_DEEPSLATE, Blocks.DRAGON_EGG,
			Blocks.TURTLE_EGG, Blocks.SNIFFER_EGG, Blocks.FROGSPAWN,
			Blocks.RESPAWN_ANCHOR, Blocks.LODESTONE, Blocks.NOTE_BLOCK, Blocks.REDSTONE_LAMP);

	/** Blocks from other mods that modpacks want left alone: {@code #the_dream_table:protected}. */
	public static final TagKey<Block> PROTECTED = TagKey.of(RegistryKeys.BLOCK, new Identifier(TheDreamTableMod.MOD_ID, "protected"));

	private ObstacleRules() {
	}

	/**
	 * Only plain blocks are cleared. Anything that stores data, can't be broken, drives or listens to redstone,
	 * spawns something when broken, or can't be gotten back stays put and stops the build instead.
	 */
	static boolean clearable(BlockView world, BlockPos tablePos, BlockPos pos, BlockState state) {
		Block block = state.getBlock();
		if (pos.equals(tablePos) || block instanceof DreamTableBlock || KEEP.contains(block) || state.isIn(PROTECTED)) {
			return false;
		}
		if (state.hasBlockEntity() || state.getHardness(world, pos) < 0.0F || state.emitsRedstonePower()) {
			return false;
		}
		if (block instanceof PistonBlock || block instanceof PistonHeadBlock || block instanceof TntBlock || block instanceof InfestedBlock) {
			return false;
		}
		return !(block instanceof AbstractRailBlock);
	}

	/**
	 * Whether a layer clear removes this block. Air has nothing to remove, and a step cell is left to the step path, which
	 * decides it when its turn comes. Fluids and plants are removed too, so a pond or grass inside the area goes with the rest.
	 */
	static boolean clearableInLayer(BlockView world, BlockPos tablePos, BlockPos pos, BlockState state, boolean stepCell) {
		return !stepCell && !state.isAir() && clearable(world, tablePos, pos, state);
	}

	/**
	 * Removes blocks a layer clear chose, with no drops and no neighbor updates (the caller settles them in one pass). The other
	 * half of a double block goes too, when it is given. Only the break effect is shown, and only for solid blocks, as afterReplace does.
	 */
	static void removeWithoutDrops(ServerWorld world, BlockPos pos, BlockState state, @Nullable BlockPos partner) {
		world.setBlockState(pos, Blocks.AIR.getDefaultState(), FLAGS);
		if (partner != null) {
			world.setBlockState(partner, Blocks.AIR.getDefaultState(), FLAGS);
		}
		if (!state.isReplaceable()) {
			world.syncWorldEvent(WorldEvents.BLOCK_BROKEN, pos, Block.getRawIdFromState(state));
		}
	}

	/**
	 * The other half of a door, tall plant or similar double block, when it really is paired with this one; null for a single block
	 * or a lone half whose other half is gone. Its own cell is checked like any other before both are removed.
	 */
	static @Nullable BlockPos partnerOf(BlockView world, BlockPos pos, BlockState state) {
		if (!state.contains(Properties.DOUBLE_BLOCK_HALF)) {
			return null;
		}
		BlockPos other = state.get(Properties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER ? pos.down() : pos.up();
		return isOtherHalf(state, world.getBlockState(other)) ? other : null;
	}

	/**
	 * Finishes removing the block that was replaced, once the new block is confirmed in place:
	 * its drops, its break effect, and the other half of a door or tall plant. Nothing is updated around it,
	 * so nothing nearby falls, pops off, or fires. Both halves drop once, from the lower half.
	 * Liquids, fire and other replaceable blocks were simply overwritten and drop nothing.
	 * Running only after a successful placement means a failed attempt can never drop an obstacle twice.
	 */
	static void afterReplace(ServerWorld world, PlayerEntity actor, BlockPos pos, BlockState state, boolean drop) {
		if (state.isAir()) {
			return;
		}
		BlockPos dropPos = pos;
		BlockState dropState = state;
		if (state.contains(Properties.DOUBLE_BLOCK_HALF)) {
			boolean upper = state.get(Properties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER;
			BlockPos otherPos = upper ? pos.down() : pos.up();
			BlockState other = world.getBlockState(otherPos);
			if (isOtherHalf(state, other) && BuildPermission.canBreak(world, actor, otherPos, other)) {
				if (upper) {
					dropPos = otherPos;
					dropState = other;
				}
				world.setBlockState(otherPos, other.getFluidState().getBlockState(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
			}
		}
		if (state.isReplaceable()) {
			return;
		}
		world.syncWorldEvent(WorldEvents.BLOCK_BROKEN, pos, Block.getRawIdFromState(state));
		// Ores, obsidian and ancient debris drop nothing, so clearing isn't free mining; everything else drops as before.
		if (drop && !minedResource(dropState)) {
			Block.dropStacks(dropState, world, dropPos);
		}
	}

	/** Blocks whose drops are a mining reward: every vanilla ore tag, nether quartz ore, obsidian and ancient debris. */
	static boolean minedResource(BlockState state) {
		return state.isIn(BlockTags.COAL_ORES) || state.isIn(BlockTags.IRON_ORES) || state.isIn(BlockTags.COPPER_ORES)
				|| state.isIn(BlockTags.GOLD_ORES) || state.isIn(BlockTags.REDSTONE_ORES) || state.isIn(BlockTags.LAPIS_ORES)
				|| state.isIn(BlockTags.DIAMOND_ORES) || state.isIn(BlockTags.EMERALD_ORES) || state.isOf(Blocks.NETHER_QUARTZ_ORE)
				|| state.isOf(Blocks.OBSIDIAN) || state.isOf(Blocks.CRYING_OBSIDIAN) || state.isOf(Blocks.ANCIENT_DEBRIS);
	}

	private static boolean isOtherHalf(BlockState state, BlockState other) {
		return other.isOf(state.getBlock())
				&& other.contains(Properties.DOUBLE_BLOCK_HALF)
				&& other.get(Properties.DOUBLE_BLOCK_HALF) != state.get(Properties.DOUBLE_BLOCK_HALF);
	}
}
