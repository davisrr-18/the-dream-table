package com.davisr.thedreamtable.farm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FarmNameTest {
	@Test
	void trimsNamesAndTreatsEmptyInputAsReset() {
		FarmNames.Validation named = FarmNames.validate("\u2003River Cane\u2003");
		FarmNames.Validation reset = FarmNames.validate(" \t ");

		assertEquals(FarmNames.Result.VALID, named.result());
		assertEquals("River Cane", named.name());
		assertEquals(FarmNames.Result.RESET, reset.result());
		assertNull(reset.name());
	}

	@Test
	void countsUnicodeCodePointsRatherThanUtf16Units() {
		String thirtyTwo = "🌾".repeat(32);

		assertEquals(FarmNames.Result.VALID, FarmNames.validate(thirtyTwo).result());
		assertEquals(FarmNames.Result.TOO_LONG, FarmNames.validate(thirtyTwo + "x").result());
	}

	@Test
	void rejectsControlsFormattingCodesAndInvalidSurrogates() {
		assertEquals(FarmNames.Result.INVALID_CHARACTER, FarmNames.validate("Farm\nName").result());
		assertEquals(FarmNames.Result.INVALID_CHARACTER, FarmNames.validate("Farm§a").result());
		assertEquals(FarmNames.Result.INVALID_UNICODE, FarmNames.validate("Farm\uD800").result());
		assertEquals(FarmNames.Result.INVALID_UNICODE, FarmNames.validate("Farm\uDC00").result());
	}
}
