package com.davisr.thedreamtable.farm.capture;

import net.minecraft.util.math.BlockPos;

/**
 * The corners of a capture and how far the box's top and bottom were moved. The first click sets corner 1 (the block aimed at),
 * then corner 2 follows the player's body until a second click fixes it; a third click starts again. The caller feeds the
 * position of the body each tick, so the box is drawn live while the player walks.
 */
public final class CaptureSelection {
	private BlockPos first;
	private BlockPos second;
	private BlockPos following;
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
		following = null;
		topShift = 0;
		bottomShift = 0;
	}

	/**
	 * A click: sets corner 1 at {@code target} and starts following {@code body}, or fixes corner 2 at {@code body}, or, once the box
	 * is fixed, starts a new box. A click with no position is ignored.
	 */
	public void click(BlockPos target, BlockPos body) {
		if (target == null || body == null) {
			return;
		}
		if (first == null || second != null) {
			clear();
			first = target.toImmutable();
			following = body.toImmutable();
		} else {
			second = body.toImmutable();
		}
	}

	/** Moves corner 2 to the player's body while it is not fixed. */
	public void follow(BlockPos body) {
		if (first != null && second == null && body != null) {
			following = body.toImmutable();
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
		CaptureBox base = CaptureBox.of(first, second);
		return base.max().getY() + top >= base.min().getY() + bottom;
	}

	/** The box as it stands: from corner 1 to corner 2, or to the body while corner 2 follows it. Null before the first click. */
	public CaptureBox box() {
		if (first == null) {
			return null;
		}
		CaptureBox base = CaptureBox.of(first, second != null ? second : following != null ? following : first);
		if (second == null) {
			return base;
		}
		return new CaptureBox(base.min().up(bottomShift), base.max().up(topShift));
	}
}
