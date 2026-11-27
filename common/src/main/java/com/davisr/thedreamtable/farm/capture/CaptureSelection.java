package com.davisr.thedreamtable.farm.capture;

import net.minecraft.util.math.BlockPos;

/**
 * The corners a player has picked in map mode and how far the box's top and bottom were moved. The first click sets a corner,
 * the second fixes the box, a third starts again. Until the box is fixed it follows the aim, so it is drawn live.
 */
public final class CaptureSelection {
	private BlockPos first;
	private BlockPos second;
	private int topShift;
	private int bottomShift;

	public boolean started() {
		return first != null;
	}

	public boolean fixed() {
		return second != null;
	}

	public void clear() {
		first = null;
		second = null;
		topShift = 0;
		bottomShift = 0;
	}

	/** A click on the ground: the first or the opposite corner, or the first corner of a new box once one is fixed. A click on nothing is ignored. */
	public void click(BlockPos aimed) {
		if (aimed == null) {
			return;
		}
		if (first == null || second != null) {
			clear();
			first = aimed.toImmutable();
		} else {
			second = aimed.toImmutable();
		}
	}

	/** Moves the top face up (positive) or down; the box keeps at least one block of height. Only once the box is fixed. */
	public void moveTop(int blocks) {
		if (fixed() && blockRange(topShift + blocks, bottomShift)) {
			topShift += blocks;
		}
	}

	/** Moves the bottom face up (positive, the box gets shorter) or down. Only once the box is fixed. */
	public void moveBottom(int blocks) {
		if (fixed() && blockRange(topShift, bottomShift + blocks)) {
			bottomShift += blocks;
		}
	}

	private boolean blockRange(int top, int bottom) {
		BlockPos low = low();
		BlockPos high = high();
		return high.getY() + top >= low.getY() + bottom;
	}

	private BlockPos low() {
		return CaptureBox.of(first, second).min();
	}

	private BlockPos high() {
		return CaptureBox.of(first, second).max();
	}

	/** The box as it stands: from the first corner to the second, or to {@code aimed} while the second is not set. Null before the first click. */
	public CaptureBox box(BlockPos aimed) {
		if (first == null) {
			return null;
		}
		BlockPos other = second != null ? second : aimed != null ? aimed : first;
		CaptureBox base = CaptureBox.of(first, other);
		if (second == null) {
			return base;
		}
		return new CaptureBox(base.min().up(bottomShift), base.max().up(topShift));
	}
}
