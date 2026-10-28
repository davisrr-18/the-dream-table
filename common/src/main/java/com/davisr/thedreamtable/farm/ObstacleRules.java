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
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.WorldEvents;

import java.util.Set;

/** What the builder may remove from a farm spot, and how it removes it. */
final class ObstacleRules {
	private static final Set<Block> KEEP = Set.of(
			Blocks.BUDDING_AMETHYST, Blocks.REINFORCED_DEEPSLATE, Blocks.DRAGON_EGG,
			Blocks.TURTLE_EGG, Blocks.SNIFFER_EGG, Blocks.FROGSPAWN,
			Blocks.RESPAWN_ANCHOR, Blocks.LODESTONE, Blocks.NOTE_BLOCK, Blocks.REDSTONE_LAMP);

	/** Blocks from other mods that modpacks want left alone: {@code #the_dream_table:protected}. */
	public static final TagKey<Block> PROTECTED = TagKey.of(RegistryKeys.BLOCK, Identifier.of(TheDreamTableMod.MOD_ID, "protected"));

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
		if (drop) {
			Block.dropStacks(dropState, world, dropPos);
		}
	}

	private static boolean isOtherHalf(BlockState state, BlockState other) {
		return other.isOf(state.getBlock())
				&& other.contains(Properties.DOUBLE_BLOCK_HALF)
				&& other.get(Properties.DOUBLE_BLOCK_HALF) != state.get(Properties.DOUBLE_BLOCK_HALF);
	}
}
