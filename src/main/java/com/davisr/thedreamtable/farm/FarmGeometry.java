package com.davisr.thedreamtable.farm;

import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public final class FarmGeometry {
	public static final int FORWARD_GAP = 2;
	public static final int AIM_RANGE = 48;

	private FarmGeometry() {
	}

	public static BlockRotation rotation(Direction facing) {
		return switch (facing) {
			case EAST -> BlockRotation.COUNTERCLOCKWISE_90;
			case WEST -> BlockRotation.CLOCKWISE_90;
			case NORTH -> BlockRotation.CLOCKWISE_180;
			default -> BlockRotation.NONE;
		};
	}

	/** Position of a step when the farm sits in front of the table. */
	public static BlockPos toWorld(BlockPos table, Direction facing, FarmStep step) {
		BlockPos local = new BlockPos(step.x(), step.y(), step.z()).rotate(rotation(facing));
		return table.offset(facing, FORWARD_GAP).add(local);
	}

	/** Position of a step when the farm is anchored at a block the player aimed at. */
	public static BlockPos toAimed(BlockPos anchor, Direction facing, FarmTemplate template, FarmStep step) {
		BlockPos center = template.center();
		BlockPos local = new BlockPos(step.x() - center.getX(), step.y() - center.getY(), step.z() - center.getZ()).rotate(rotation(facing));
		return anchor.add(local);
	}
}
