package com.davisr.thedreamtable.farm.capture;

import com.davisr.thedreamtable.farm.capture.CaptureSteps.Hint;
import com.davisr.thedreamtable.farm.capture.CaptureSteps.State;
import com.davisr.thedreamtable.farm.capture.CaptureSteps.Step;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CaptureStepsTest {
	private static final BlockPos A = new BlockPos(1, 64, 1);

	@Test
	void theCurrentStepFollowsTheSelection() {
		CaptureSelection selection = new CaptureSelection();
		assertEquals(Step.FIRST_CORNER, CaptureSteps.current(selection));
		selection.click(A);
		assertEquals(Step.SECOND_CORNER, CaptureSteps.current(selection));
		selection.click(new BlockPos(5, 64, 5));
		assertEquals(Step.ADJUST_AND_SAVE, CaptureSteps.current(selection));
		selection.click(A);
		assertEquals(Step.SECOND_CORNER, CaptureSteps.current(selection), "a third click starts a new box");
	}

	@Test
	void earlierStepsAreDoneTheCurrentOneIsHighlightedAndLaterOnesWait() {
		assertEquals(State.CURRENT, CaptureSteps.stateOf(Step.FIRST_CORNER, Step.FIRST_CORNER));
		assertEquals(State.PENDING, CaptureSteps.stateOf(Step.SECOND_CORNER, Step.FIRST_CORNER));
		assertEquals(State.DONE, CaptureSteps.stateOf(Step.FIRST_CORNER, Step.SECOND_CORNER));
		assertEquals(State.CURRENT, CaptureSteps.stateOf(Step.SECOND_CORNER, Step.SECOND_CORNER));
		assertEquals(State.PENDING, CaptureSteps.stateOf(Step.ADJUST_AND_SAVE, Step.SECOND_CORNER));
		assertEquals(State.DONE, CaptureSteps.stateOf(Step.FIRST_CORNER, Step.ADJUST_AND_SAVE));
		assertEquals(State.DONE, CaptureSteps.stateOf(Step.SECOND_CORNER, Step.ADJUST_AND_SAVE));
		assertEquals(State.CURRENT, CaptureSteps.stateOf(Step.ADJUST_AND_SAVE, Step.ADJUST_AND_SAVE));
	}

	@Test
	void eachStepShowsOnlyItsOwnKeys() {
		assertEquals(List.of(Hint.MARK, Hint.EXIT), CaptureSteps.hints(Step.FIRST_CORNER));
		assertEquals(List.of(Hint.FIX, Hint.HEIGHT, Hint.EXIT), CaptureSteps.hints(Step.SECOND_CORNER));
		assertEquals(List.of(Hint.HEIGHT, Hint.SHRINK, Hint.NAME, Hint.EXIT), CaptureSteps.hints(Step.ADJUST_AND_SAVE));
	}
}
