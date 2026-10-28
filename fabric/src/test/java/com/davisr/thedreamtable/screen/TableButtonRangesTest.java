package com.davisr.thedreamtable.screen;

import org.junit.jupiter.api.Test;

import static com.davisr.thedreamtable.screen.TableButtons.DynamicRange.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TableButtonRangesTest {
	@Test
	void boundsEveryLegacyDynamicRange() {
		assertEquals(NONE, TableButtons.dynamicRange(199));
		assertEquals(SCHEMATIC, TableButtons.dynamicRange(200));
		assertEquals(SCHEMATIC, TableButtons.dynamicRange(999));
		assertEquals(SWAP_OPEN, TableButtons.dynamicRange(1000));
		assertEquals(SWAP_OPEN, TableButtons.dynamicRange(1999));
		assertEquals(SWAP_PICK, TableButtons.dynamicRange(2000));
		assertEquals(SWAP_PICK, TableButtons.dynamicRange(2999));
		assertEquals(NONE, TableButtons.dynamicRange(3000));
		assertEquals(CUSTOM_PACE, TableButtons.dynamicRange(3001));
		assertEquals(CUSTOM_PACE, TableButtons.dynamicRange(3100));
		assertEquals(NONE, TableButtons.dynamicRange(3101));
	}

	@Test
	void legacyDynamicRangesDoNotCaptureFutureTabButtons() {
		assertEquals(NONE, TableButtons.dynamicRange(3201));
		assertFalse(TableButtons.press(null, null, 3201));
		assertFalse(TableButtons.press(null, null, Integer.MAX_VALUE));
	}
}
