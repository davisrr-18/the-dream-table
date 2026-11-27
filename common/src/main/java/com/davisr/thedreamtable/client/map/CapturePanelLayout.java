package com.davisr.thedreamtable.client.map;

import java.util.List;

/**
 * The capture panel's layout. The main panel (title, three steps, the size line) never moves or resizes: its width is the widest
 * content it can ever show, worked out from fixed strings and not from the state, and its height is fixed. A problem is a box of
 * its own under it, so it can come and go without touching the panel. Pure, so it is tested without a client.
 */
final class CapturePanelLayout {
	static final int MARGIN = 6;
	static final int PADDING = KeyChips.PADDING;
	static final int ROW = KeyChips.ROW;
	static final int LINE = 11;
	static final int STEP_ROWS = 3;
	/** The space between the panel and the problem box under it. */
	static final int PROBLEM_GAP = 2;
	/** The lines a problem takes; a longer text is cut. */
	static final int PROBLEM_LINES = 3;
	/** The narrowest the content may be squeezed to by a small window. */
	private static final int MIN_INNER = 40;

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

	/**
	 * The content width: the widest of the given content widths (the title, the step labels with their marks, and the widest size line
	 * the panel can show, not the one it shows now), capped by the window. It takes no state, so it is the same every frame.
	 */
	static int innerWidth(int[] contentWidths, int windowWidth) {
		int widest = 0;
		for (int width : contentWidths) {
			widest = Math.max(widest, width);
		}
		return Math.max(MIN_INNER, Math.min(widest, windowWidth - 2 * (MARGIN + PADDING)));
	}

	static int width(int innerWidth) {
		return innerWidth + 2 * PADDING;
	}

	/** The panel's height: title, steps and the size line (one row whichever {@link SizeLine} shows). It takes no state on purpose. */
	static int height() {
		return ROW * (1 + STEP_ROWS + 1) + 2 * PADDING - (ROW - 12);
	}

	/** The height of the problem box for a problem of this many lines; nothing when there is none. */
	static int problemBoxHeight(int lines) {
		return lines <= 0 ? 0 : LINE * Math.min(lines, PROBLEM_LINES) + 2 * PADDING - 3;
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

	/** The lines of a problem that fit its box. */
	static <T> List<T> problemSlot(List<T> lines) {
		return lines.size() <= PROBLEM_LINES ? lines : lines.subList(0, PROBLEM_LINES);
	}

	/** Whether a problem of this many lines is cut by its box, so its last line is to end with an ellipsis. */
	static boolean problemCut(int lines) {
		return lines > PROBLEM_LINES;
	}
}
