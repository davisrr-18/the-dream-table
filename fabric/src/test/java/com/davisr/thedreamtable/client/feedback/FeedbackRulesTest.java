package com.davisr.thedreamtable.client.feedback;

import com.davisr.thedreamtable.client.feedback.FeedbackRules.Block;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Decides whether Send is enabled and why not, so the button's second line always names the reason. */
class FeedbackRulesTest {
	private static Optional<Block> blocked(String text, boolean available, boolean busy, long now, long nextAllowed) {
		return FeedbackRules.blocked(text, available, busy, now, nextAllowed);
	}

	@Test
	void notAvailableWinsOverEverything() {
		assertEquals(Optional.of(Block.NOT_AVAILABLE), blocked("", false, true, 0, 100));
	}

	@Test
	void busyWinsOverCooldownAndLength() {
		assertEquals(Optional.of(Block.BUSY), blocked("", true, true, 0, 100));
	}

	@Test
	void cooldownWinsOverLength() {
		assertEquals(Optional.of(Block.COOLDOWN), blocked("", true, false, 50, 100));
		assertEquals(Optional.of(Block.TOO_SHORT), blocked("", true, false, 100, 100));
	}

	@Test
	void lengthLimitsAreTenAndTwoThousand() {
		assertEquals(Optional.of(Block.TOO_SHORT), blocked("123456789", true, false, 0, 0));
		assertEquals(Optional.empty(), blocked("1234567890", true, false, 0, 0));
		assertEquals(Optional.empty(), blocked("a".repeat(2000), true, false, 0, 0));
		assertEquals(Optional.of(Block.TOO_LONG), blocked("a".repeat(2001), true, false, 0, 0));
	}

	@Test
	void whitespaceOnlyTextIsTooShort() {
		assertEquals(Optional.of(Block.TOO_SHORT), blocked("          \n  ", true, false, 0, 0));
	}

	@Test
	void pauseStartsWhenASendFinishes() {
		assertEquals(1000 + FeedbackRules.COOLDOWN_MILLIS, FeedbackRules.nextAllowedAfter(1000));
		assertFalse(FeedbackRules.COOLDOWN_MILLIS <= 0);
	}
}
