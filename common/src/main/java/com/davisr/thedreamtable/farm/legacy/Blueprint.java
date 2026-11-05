package com.davisr.thedreamtable.farm.legacy;

import com.davisr.thedreamtable.farm.FarmStep;
import net.minecraft.block.BambooBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.BambooLeaves;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.WallMountLocation;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.RailShape;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;

import java.util.List;

/** Block helpers for the farms built in code, used when a bundled schematic can't be read. */
final class Blueprint {
	private Blueprint() {
	}

	static void block(List<FarmStep> steps, int x, int y, int z, BlockState state, Item item) {
		steps.add(FarmStep.block(x, y, z, state, item));
	}

	static void dirt(List<FarmStep> steps, int x, int y, int z) {
		block(steps, x, y, z, Blocks.DIRT.getDefaultState(), Items.DIRT);
	}

	static void hopper(List<FarmStep> steps, int x, int y, int z, Direction facing) {
		block(steps, x, y, z, Blocks.HOPPER.getDefaultState().with(Properties.HOPPER_FACING, facing), Items.HOPPER);
	}

	static void chest(List<FarmStep> steps, int x, int y, int z) {
		block(steps, x, y, z, Blocks.CHEST.getDefaultState(), Items.CHEST);
	}

	/** The head is free: the bed item is spent on the foot. */
	static void bed(List<FarmStep> steps, int x, int y, int z) {
		BlockState foot = Blocks.RED_BED.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.SOUTH).with(Properties.BED_PART, BedPart.FOOT);
		block(steps, x, y, z, foot, Items.RED_BED);
		block(steps, x, y, z + 1, foot.with(Properties.BED_PART, BedPart.HEAD), null);
	}

	static BlockState bambooStalk() {
		return Blocks.BAMBOO.getDefaultState().with(BambooBlock.AGE, 0).with(BambooBlock.STAGE, 0).with(BambooBlock.LEAVES, BambooLeaves.NONE);
	}

	static BlockState rail() {
		return Blocks.RAIL.getDefaultState().with(Properties.RAIL_SHAPE, RailShape.EAST_WEST);
	}

	static BlockState poweredRail() {
		return Blocks.POWERED_RAIL.getDefaultState().with(Properties.STRAIGHT_RAIL_SHAPE, RailShape.EAST_WEST);
	}

	static BlockState openTrapdoor(Direction facing) {
		return Blocks.OAK_TRAPDOOR.getDefaultState()
				.with(Properties.HORIZONTAL_FACING, facing)
				.with(Properties.OPEN, true)
				.with(Properties.BLOCK_HALF, BlockHalf.BOTTOM);
	}

	static BlockState floorButton() {
		return Blocks.OAK_BUTTON.getDefaultState().with(Properties.WALL_MOUNT_LOCATION, WallMountLocation.FLOOR).with(Properties.HORIZONTAL_FACING, Direction.NORTH);
	}
}
