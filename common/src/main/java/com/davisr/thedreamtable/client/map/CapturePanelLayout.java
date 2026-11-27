package com.davisr.thedreamtable.client.map;

import java.util.List;

/**
 * The capture panel's fixed layout. Every section keeps its place whatever the capture is doing: the width is fixed, the size is
 * always one line (a "counting..." text replaces the count in place), and the problem has a slot of its own that is reserved even
 * when empty. Pure, so it is tested without a client.
 */
final class CapturePanelLayout {
	static final int MARGIN = 6;
	static final int PADDING = KeyChips.PADDING;
	static final int ROW = KeyChips.ROW;
	static final int LINE = 11;
	/** The panel's content width: narrow enough to stay clear of the centred notices at any GUI scale. */
	static final int INNER_WIDTH = 200;
	static final int STEP_ROWS = 3;
	/** The lines kept for a problem; a longer text is cut. */
	static final int PROBLEM_LINES = 3;

	/** What the size line shows. */
	enum SizeLine {
		/** Before the first corner: nothing, but the line is still there. */
		NONE,
		/** The size of the box. */
		SIZE,
		/** The size, with the block count still being read. */
		COUNTING,
		/** The size and the block count. */
		BLOCKS
	}

	private CapturePanelLayout() {
	}

	static int width() {
		return INNER_WIDTH + 2 * PADDING;
	}

	/**
	 * The panel's height. It takes no state on purpose: the title, the steps and the size line (always one row, whichever
	 * {@link SizeLine} shows) and the problem slot are all reserved, so nothing the capture does can change it.
	 */
	static int height() {
		return ROW * (1 + STEP_ROWS + 1) + LINE * PROBLEM_LINES + 2 * PADDING - (ROW - 12);
	}

	static SizeLine sizeLine(boolean started, boolean fixed, boolean counting, boolean hasProblem) {
		if (!started) {
			return SizeLine.NONE;
		}
		if (!fixed || hasProblem) {
			return SizeLine.SIZE;
		}
		return counting ? SizeLine.COUNTING : SizeLine.BLOCKS;
	}

	/** The lines of a problem that fit its slot. */
	static <T> List<T> problemSlot(List<T> lines) {
		return lines.size() <= PROBLEM_LINES ? lines : lines.subList(0, PROBLEM_LINES);
	}

	/** Whether a problem of this many lines is cut by its slot, so its last line is to end with an ellipsis. */
	static boolean problemCut(int lines) {
		return lines > PROBLEM_LINES;
	}
}
