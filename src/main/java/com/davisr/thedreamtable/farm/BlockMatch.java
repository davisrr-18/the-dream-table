package com.davisr.thedreamtable.farm;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FluidBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;

import java.util.Set;

/**
 * Compares two states by what defines the block: its direction, half, axis, bed part, door hinge,
 * sign rotation, mounting face, and slab type. State that changes on its own is ignored:
 * open doors, redstone power, stair shape, and fence or pane connections.
 */
final class BlockMatch {
	private static final Set<String> DEFINING = Set.of("facing", "axis", "half", "part", "hinge", "rotation", "face");

	private BlockMatch() {
	}

	/** True when the world already holds this block, so the build can skip the step. Waterlogging counts. */
	static boolean sameBlock(BlockState existing, BlockState expected) {
		return sameShape(existing, expected) && waterlogging(existing) == waterlogging(expected);
	}

	/** Like {@link #sameBlock} but ignores waterlogging, which the farm's own water can change after placement. */
	static boolean sameShape(BlockState existing, BlockState expected) {
		if (!existing.isOf(expected.getBlock())) {
			return false;
		}
		for (Property<?> property : expected.getProperties()) {
			if (defines(expected, property) && !existing.get(property).equals(expected.get(property))) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Like {@link #sameShape}, but also accepts what the game turns the block into by itself: farmland that dried
	 * into dirt, a bamboo shoot that grew into bamboo. Used to tell whether a block is still the farm's, when taking it
	 * down or counting what a repair would place.
	 */
	static boolean stillFarm(BlockState existing, BlockState expected) {
		return sameShape(existing, expected) || sameFamily(existing, expected, Blocks.FARMLAND, Blocks.DIRT)
				|| sameFamily(existing, expected, Blocks.BAMBOO_SAPLING, Blocks.BAMBOO);
	}

	private static boolean sameFamily(BlockState existing, BlockState expected, Block first, Block second) {
		return (expected.isOf(first) || expected.isOf(second)) && (existing.isOf(first) || existing.isOf(second));
	}

	/** Liquids are told apart by level, since a source and flowing water are different steps. Composters and cauldrons are not: players fill them. */
	private static boolean defines(BlockState state, Property<?> property) {
		return DEFINING.contains(property.getName())
				|| (property == Properties.SLAB_TYPE && state.getBlock() instanceof SlabBlock)
				|| (property == Properties.LEVEL_15 && state.getBlock() instanceof FluidBlock);
	}

	private static boolean waterlogging(BlockState state) {
		return state.contains(Properties.WATERLOGGED) && state.get(Properties.WATERLOGGED);
	}
}
