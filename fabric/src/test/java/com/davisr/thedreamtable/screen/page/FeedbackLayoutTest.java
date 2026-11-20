package com.davisr.thedreamtable.screen.page;

import org.junit.jupiter.api.Test;

import static com.davisr.thedreamtable.screen.ui.Theme.BUTTON_HEIGHT;
import static com.davisr.thedreamtable.screen.ui.Theme.CARD_GAP;
import static com.davisr.thedreamtable.screen.ui.Theme.CARD_HEIGHT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The feedback section's rows are the single source for both its drawing and the content height it adds to Options. */
class FeedbackLayoutTest {
	@Test
	void theCardStartsAtTheTopAndTheExplanationFollowsIt() {
		FeedbackLayout rows = FeedbackLayout.of(100, 2, false);
		assertEquals(100, rows.card());
		assertEquals(100 + CARD_HEIGHT + CARD_GAP, rows.explanation());
	}

	@Test
	void aSwitchedOffSectionEndsAfterItsExplanation() {
		FeedbackLayout rows = FeedbackLayout.of(0, 2, false);
		assertEquals(rows.explanation() + 2 * FeedbackLayout.LINE + CARD_GAP, rows.bottom());
		assertEquals(rows.bottom(), rows.field());
		assertEquals(rows.bottom(), rows.send());
	}

	@Test
	void theFormRowsStackWithoutOverlap() {
		FeedbackLayout rows = FeedbackLayout.of(0, 3, true);
		assertTrue(rows.field() >= rows.explanation() + 3 * FeedbackLayout.LINE);
		assertTrue(rows.counter() >= rows.field() + FeedbackLayout.FIELD_HEIGHT);
		assertTrue(rows.categories() >= rows.counter() + FeedbackLayout.LINE);
		assertTrue(rows.send() >= rows.categories() + BUTTON_HEIGHT);
		assertEquals(rows.send() + BUTTON_HEIGHT, rows.bottom());
	}

	@Test
	void moreExplanationLinesPushTheFormDown() {
		assertTrue(FeedbackLayout.of(0, 4, true).field() > FeedbackLayout.of(0, 2, true).field());
	}

	@Test
	void theFormAddsRowsBelowTheSwitchedOffSection() {
		assertTrue(FeedbackLayout.of(0, 2, true).bottom() > FeedbackLayout.of(0, 2, false).bottom());
	}
}
