package com.davisr.thedreamtable.block.entity;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/** The anchor block and facing the player picked for the farm. No anchor means the farm sits beside the table. */
final class FarmPlacement {
	private BlockPos anchor;
	private Direction facing = Direction.SOUTH;
	private boolean mirrored;

	BlockPos anchor() {
		return anchor;
	}

	Direction facing() {
		return facing;
	}

	boolean mirrored() {
		return mirrored;
	}

	boolean placed() {
		return anchor != null;
	}

	void set(BlockPos anchor, Direction facing, boolean mirrored) {
		this.anchor = anchor;
		this.facing = horizontal(facing);
		this.mirrored = mirrored;
	}

	void clear() {
		anchor = null;
		facing = Direction.SOUTH;
		mirrored = false;
	}

	void write(NbtCompound nbt) {
		nbt.putString("FarmFacing", facing.asString());
		if (mirrored) {
			nbt.putBoolean("FarmMirror", true);
		}
		if (anchor != null) {
			nbt.putInt("AnchorX", anchor.getX());
			nbt.putInt("AnchorY", anchor.getY());
			nbt.putInt("AnchorZ", anchor.getZ());
		}
	}

	void read(NbtCompound nbt) {
		facing = horizontal(Direction.byName(nbt.getString("FarmFacing")));
		mirrored = nbt.getBoolean("FarmMirror");
		anchor = nbt.contains("AnchorX", NbtElement.INT_TYPE) ? new BlockPos(nbt.getInt("AnchorX"), nbt.getInt("AnchorY"), nbt.getInt("AnchorZ")) : null;
	}

	private static Direction horizontal(Direction facing) {
		return facing != null && facing.getAxis().isHorizontal() ? facing : Direction.SOUTH;
	}
}
