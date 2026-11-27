package com.davisr.thedreamtable.client.map;

import com.davisr.thedreamtable.client.map.CapturePanelLayout.SizeLine;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapturePanelLayoutTest {
	@Test
	void theHeightTakesNoStateSoNothingTheCaptureDoesCanChangeIt() {
		// height() has no parameter on purpose; this pins its value to the reserved rows: title, three steps, one size row, three problem lines.
		int expected = CapturePanelLayout.ROW * 5 + CapturePanelLayout.LINE * 3 + 2 * CapturePanelLayout.PADDING - (CapturePanelLayout.ROW - 12);
		assertEquals(expected, CapturePanelLayout.height());
	}

	@Test
	void everySizeLineStateIsOneLineThatReplacesTheCountInPlace() {
		assertEquals(SizeLine.NONE, CapturePanelLayout.sizeLine(false, false, false, false));
		assertEquals(SizeLine.SIZE, CapturePanelLayout.sizeLine(true, false, false, false));
		assertEquals(SizeLine.COUNTING, CapturePanelLayout.sizeLine(true, true, true, false));
		assertEquals(SizeLine.BLOCKS, CapturePanelLayout.sizeLine(true, true, false, false));
		assertEquals(SizeLine.SIZE, CapturePanelLayout.sizeLine(true, true, false, true), "a problem shows the plain size");
	}

	@Test
	void theWidthIsFixedAndTheProblemSlotKeepsAtMostItsLines() {
		assertEquals(CapturePanelLayout.INNER_WIDTH + 2 * CapturePanelLayout.PADDING, CapturePanelLayout.width());
		assertEquals(List.of("a", "b"), CapturePanelLayout.problemSlot(List.of("a", "b")));
		assertEquals(List.of("a", "b", "c"), CapturePanelLayout.problemSlot(List.of("a", "b", "c", "d", "e")));
		assertEquals(List.of(), CapturePanelLayout.problemSlot(List.of()));
	}

	@Test
	void aProblemLongerThanItsSlotIsMarkedCut() {
		assertFalse(CapturePanelLayout.problemCut(0));
		assertFalse(CapturePanelLayout.problemCut(3));
		assertTrue(CapturePanelLayout.problemCut(4));
	}
}
