package com.davisr.thedreamtable.farm;

import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.HashMap;
import java.util.Map;

public final class FarmGeometry {
	public static final int FORWARD_GAP = 2;
	public static final int AIM_RANGE = 48;

	private static final Map<String, int[]> CENTERS = new HashMap<>();

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

	public static BlockPos toWorld(BlockPos table, Direction facing, FarmStep step) {
		BlockPos local = new BlockPos(step.x(), step.y(), step.z()).rotate(rotation(facing));
		return table.offset(facing, FORWARD_GAP).add(local);
	}

	public static BlockPos toAimed(BlockPos anchor, Direction facing, FarmTemplate template, FarmStep step) {
		int[] center = center(template);
		BlockPos local = new BlockPos(step.x() - center[0], step.y() - center[1], step.z() - center[2]).rotate(rotation(facing));
		return anchor.add(local);
	}

	private static int[] center(FarmTemplate template) {
		return CENTERS.computeIfAbsent(template.id(), id -> {
			int minX = Integer.MAX_VALUE;
			int maxX = Integer.MIN_VALUE;
			int minY = Integer.MAX_VALUE;
			int minZ = Integer.MAX_VALUE;
			int maxZ = Integer.MIN_VALUE;
			for (FarmStep step : template.steps()) {
				minX = Math.min(minX, step.x());
				maxX = Math.max(maxX, step.x());
				minY = Math.min(minY, step.y());
				minZ = Math.min(minZ, step.z());
				maxZ = Math.max(maxZ, step.z());
			}
			return new int[] {Math.floorDiv(minX + maxX, 2), minY, Math.floorDiv(minZ + maxZ, 2)};
		});
	}
}
