package com.davisr.thedreamtable.farm;

import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.enums.ChestType;
import net.minecraft.block.enums.StairShape;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public final class FarmGeometry {
	public static final int FORWARD_GAP = 2;
	public static final int AIM_RANGE = 48;
	/** The flip a mirrored farm gets before it is turned: east and west swap, in the template's own frame. */
	public static final BlockMirror MIRROR = BlockMirror.FRONT_BACK;

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
	public static BlockPos toWorld(BlockPos table, Direction facing, boolean mirrored, FarmStep step) {
		BlockPos local = turn(step.x(), step.y(), step.z(), facing, mirrored);
		return table.offset(facing, FORWARD_GAP).add(local);
	}

	/** Position of a step when the farm is anchored at a block the player aimed at. */
	public static BlockPos toAimed(BlockPos anchor, Direction facing, boolean mirrored, FarmTemplate template, FarmStep step) {
		BlockPos center = template.center();
		BlockPos local = turn(step.x() - center.getX(), step.y() - center.getY(), step.z() - center.getZ(), facing, mirrored);
		return anchor.add(local);
	}

	/** Mirrors the offset when asked, then turns it to the facing. A block state must be {@link #orient}ed the same way. */
	private static BlockPos turn(int x, int y, int z, Direction facing, boolean mirrored) {
		return new BlockPos(mirrored ? -x : x, y, z).rotate(rotation(facing));
	}

	/**
	 * A template block state as it stands in the world: mirrored first when asked, then turned, the same order
	 * {@link #toAimed} uses for positions. Vanilla's chest mirroring only turns the facing and leaves the halves of a
	 * double chest on the side they were, which would split the pair, so left and right are swapped here; stairs get their
	 * corner fixed the same way.
	 */
	public static BlockState orient(BlockState state, Direction facing, boolean mirrored) {
		if (mirrored) {
			StairShape stairs = state.getBlock() instanceof StairsBlock ? state.get(StairsBlock.SHAPE) : null;
			state = state.mirror(MIRROR);
			if (stairs != null) {
				state = state.with(StairsBlock.SHAPE, mirrorStairShape(stairs));
			}
			if (state.getBlock() instanceof ChestBlock && state.contains(ChestBlock.CHEST_TYPE)) {
				ChestType type = state.get(ChestBlock.CHEST_TYPE);
				if (type != ChestType.SINGLE) {
					state = state.with(ChestBlock.CHEST_TYPE, type.getOpposite());
				}
			}
		}
		return state.rotate(rotation(facing));
	}

	/**
	 * Vanilla's stairs mirroring leaves the corner on the wrong side (verified against the block shapes): it keeps
	 * inner corners as they were and swaps outer corners only for stairs facing east or west. A mirror always puts the
	 * corner on the other side, so the shape is swapped here whatever vanilla did.
	 */
	private static StairShape mirrorStairShape(StairShape shape) {
		return switch (shape) {
			case INNER_LEFT -> StairShape.INNER_RIGHT;
			case INNER_RIGHT -> StairShape.INNER_LEFT;
			case OUTER_LEFT -> StairShape.OUTER_RIGHT;
			case OUTER_RIGHT -> StairShape.OUTER_LEFT;
			default -> shape;
		};
	}
}
