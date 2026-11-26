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
	void treatsNamesOfOnlyInvisibleCharactersAsReset() {
		assertEquals(FarmNames.Result.RESET, FarmNames.validate("\u200B\u200B").result());
		assertEquals(FarmNames.Result.RESET, FarmNames.validate("\u00A0 \u00A0").result());
		assertEquals(FarmNames.Result.RESET, FarmNames.validate("\u200B\u00A0\uFEFF").result());
		assertEquals(FarmNames.Result.RESET, FarmNames.validate("\u3164\u2800\u115F\u1160\uFFA0").result());
		FarmNames.Validation named = FarmNames.validate("\u00A0River\u00A0");
		assertEquals(FarmNames.Result.VALID, named.result());
		assertEquals("River", named.name());
	}

	@Test
	void keepsJoinersInsideAName() {
		FarmNames.Validation named = FarmNames.validate("Farm \uD83D\uDC68\u200D\uD83C\uDF3E");
		assertEquals(FarmNames.Result.VALID, named.result());
		assertEquals("Farm \uD83D\uDC68\u200D\uD83C\uDF3E", named.name());
	}

	@Test
	void keepsTheTagThatEndsAFlagEmoji() {
		String flag = "Farm \uD83C\uDFF4\uDB40\uDC67\uDB40\uDC62\uDB40\uDC65\uDB40\uDC6E\uDB40\uDC67\uDB40\uDC7F";
		assertEquals(flag, FarmNames.validate(flag).name());
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
