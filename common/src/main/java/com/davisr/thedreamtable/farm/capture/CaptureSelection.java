package com.davisr.thedreamtable.farm.capture;

import net.minecraft.util.math.BlockPos;

/**
 * The corners of a capture and how far the box's top and bottom were moved. The first click sets corner 1 at the block aimed
 * at, then the box previews up to the block the crosshair aims at (fed each tick) until a second click fixes corner 2 there; a
 * third click starts again. The top and bottom can be moved from the first click on, so an arrow key is never ignored.
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
	 * A click at the aimed block: sets corner 1, or fixes corner 2 there, or, once the box is fixed, starts a new box. With no block
	 * aimed at, a click that would fix corner 2 fixes it at the corner the preview shows; any other click with no block is ignored.
	 * Fixing corner 2 keeps the top and bottom shifts made so far, unless they would turn the new box upside down: then they are dropped.
	 */
	public void click(BlockPos aimed) {
		if (first != null && second == null) {
			second = (aimed != null ? aimed : following).toImmutable();
			if (upsideDown()) {
				topShift = 0;
				bottomShift = 0;
			}
		} else if (aimed != null) {
			clear();
			first = aimed.toImmutable();
			following = first;
		}
	}

	private boolean upsideDown() {
		CaptureBox base = base();
		return base.max().getY() + topShift < base.min().getY() + bottomShift;
	}

	/** Moves the preview's far corner to the block the crosshair aims at; a null (nothing aimed at) keeps the last one. Not once corner 2 is fixed. */
	public void follow(BlockPos aimed) {
		if (first != null && second == null && aimed != null) {
			following = aimed.toImmutable();
		}
	}

	/** Moves the top face up (positive) or down. The box keeps at least one block of height. */
	public void moveTop(int blocks) {
		if (first != null && fitsHeight(topShift + blocks, bottomShift)) {
			topShift += blocks;
		}
	}

	/** Moves the bottom face up (positive, the box gets shorter) or down (negative, it grows). The box keeps at least one block of height. */
	public void moveBottom(int blocks) {
		if (first != null && fitsHeight(topShift, bottomShift + blocks)) {
			bottomShift += blocks;
		}
	}

	private boolean fitsHeight(int top, int bottom) {
		CaptureBox base = base();
		return base.max().getY() + top >= base.min().getY() + bottom;
	}

	private CaptureBox base() {
		return CaptureBox.of(first, second != null ? second : following != null ? following : first);
	}

	/** The box as it stands: from corner 1 to corner 2, or to the aimed block while corner 2 is not fixed. Null before the first click. */
	public CaptureBox box() {
		if (first == null) {
			return null;
		}
		CaptureBox base = base();
		BlockPos low = base.min().up(bottomShift);
		BlockPos high = base.max().up(topShift);
		// The preview moves under the shifts, so a box can come out upside down; it keeps one block of height.
		return new CaptureBox(low, high.getY() < low.getY() ? high.withY(low.getY()) : high);
	}
}
