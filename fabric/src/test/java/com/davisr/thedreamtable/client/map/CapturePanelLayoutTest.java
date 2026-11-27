package com.davisr.thedreamtable.client.map;

import com.davisr.thedreamtable.client.map.CapturePanelLayout.SizeLine;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapturePanelLayoutTest {
	@Test
	void theHeightTakesNoStateAndHasNoProblemSlot() {
		// height() has no parameter on purpose; it covers the title, three steps and one size row, and nothing for a problem.
		int expected = CapturePanelLayout.ROW * 5 + 2 * CapturePanelLayout.PADDING - (CapturePanelLayout.ROW - 12);
		assertEquals(expected, CapturePanelLayout.height());
	}

	@Test
	void theWidthIsTheWidestPossibleContentWhicheverSizeLineShows() {
		// The caller measures the widest each size line can be, so the width is the same for every state; the state never enters.
		int[] widths = {60, 12 + 90, 12 + 120, 12 + 100, 150, 140};
		int inner = CapturePanelLayout.innerWidth(widths, 800);
		assertEquals(150, inner);
		assertEquals(inner + 2 * CapturePanelLayout.PADDING, CapturePanelLayout.width(inner));
		assertEquals(inner, CapturePanelLayout.innerWidth(new int[] {140, 150, 100, 12 + 90, 12 + 100, 60}, 800), "the order of the contents does not matter");
	}

	@Test
	void aSmallWindowCapsTheWidthButNeverBelowAUsableMinimum() {
		int[] widths = {300};
		assertEquals(300, CapturePanelLayout.innerWidth(widths, 1000));
		assertEquals(200 - 2 * (CapturePanelLayout.MARGIN + CapturePanelLayout.PADDING), CapturePanelLayout.innerWidth(widths, 200));
		assertEquals(40, CapturePanelLayout.innerWidth(widths, 10));
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
	void theProblemIsABoxOfItsOwnWithNoHeightWhenThereIsNone() {
		assertEquals(0, CapturePanelLayout.problemBoxHeight(0));
		assertTrue(CapturePanelLayout.problemBoxHeight(1) > 0);
		assertTrue(CapturePanelLayout.problemBoxHeight(2) > CapturePanelLayout.problemBoxHeight(1));
		assertEquals(CapturePanelLayout.problemBoxHeight(3), CapturePanelLayout.problemBoxHeight(9), "a longer problem is cut to three lines");
	}

	@Test
	void theProblemKeepsAtMostItsLinesAndIsMarkedCutPastThem() {
		assertEquals(List.of("a", "b"), CapturePanelLayout.problemSlot(List.of("a", "b")));
		assertEquals(List.of("a", "b", "c"), CapturePanelLayout.problemSlot(List.of("a", "b", "c", "d", "e")));
		assertEquals(List.of(), CapturePanelLayout.problemSlot(List.of()));
		assertFalse(CapturePanelLayout.problemCut(3));
		assertTrue(CapturePanelLayout.problemCut(4));
	}
}
