package com.davisr.thedreamtable.farm.capture;

import java.util.List;

/** The three steps the capture panel shows, which one the player is in, and the keys worth showing in it. */
public final class CaptureSteps {
	public enum Step {
		FIRST_CORNER,
		SECOND_CORNER,
		ADJUST_AND_SAVE
	}

	public enum State {
		DONE,
		CURRENT,
		PENDING
	}

	/** A key of the panel's hint row. {@code MARK} and {@code FIX} are a left click; {@code HEIGHT} is Up and Down. */
	public enum Hint {
		MARK,
		FIX,
		HEIGHT,
		SHRINK,
		NAME,
		EXIT
	}

	private CaptureSteps() {
	}

	public static Step current(CaptureSelection selection) {
		return selection.fixed() ? Step.ADJUST_AND_SAVE : selection.started() ? Step.SECOND_CORNER : Step.FIRST_CORNER;
	}

	public static State stateOf(Step step, Step current) {
		return step.ordinal() < current.ordinal() ? State.DONE : step == current ? State.CURRENT : State.PENDING;
	}

	/**
	 * Only the keys of the current step: Up and Down work from the first click on, Shift only reverses them once the box is fixed.
	 * The list depends on the step alone, so the controls bar does not shift while the box is being counted; Name is shown dimmed
	 * while saving is impossible ({@link #needsSave}).
	 */
	public static List<Hint> hints(Step current) {
		return switch (current) {
			case FIRST_CORNER -> List.of(Hint.MARK, Hint.EXIT);
			case SECOND_CORNER -> List.of(Hint.FIX, Hint.HEIGHT, Hint.EXIT);
			case ADJUST_AND_SAVE -> List.of(Hint.HEIGHT, Hint.SHRINK, Hint.NAME, Hint.EXIT);
		};
	}

	/** Whether a hint only works when the capture can be saved. */
	public static boolean needsSave(Hint hint) {
		return hint == Hint.NAME;
	}
}
