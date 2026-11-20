package com.davisr.thedreamtable.screen.page;

import static com.davisr.thedreamtable.screen.ui.Theme.BUTTON_GAP;
import static com.davisr.thedreamtable.screen.ui.Theme.BUTTON_HEIGHT;
import static com.davisr.thedreamtable.screen.ui.Theme.CARD_GAP;
import static com.davisr.thedreamtable.screen.ui.Theme.CARD_HEIGHT;

/**
 * Rows of the Settings feedback section, as Y positions from its top: the switch card, the explanation, and (when the
 * form is shown) the text box, its counter, the category row and Send. {@code bottom} is where the section ends.
 */
public record FeedbackLayout(int card, int explanation, int field, int counter, int categories, int send, int bottom) {
	public static final int FIELD_HEIGHT = 56;
	public static final int LINE = 11;
	private static final int ROW_GAP = 2;

	public static FeedbackLayout of(int top, int explanationLines, boolean formShown) {
		int explanation = top + CARD_HEIGHT + CARD_GAP;
		int afterExplanation = explanation + explanationLines * LINE + CARD_GAP;
		if (!formShown) {
			return new FeedbackLayout(top, explanation, afterExplanation, afterExplanation, afterExplanation, afterExplanation, afterExplanation);
		}
		int counter = afterExplanation + FIELD_HEIGHT + ROW_GAP;
		int categories = counter + LINE + ROW_GAP;
		int send = categories + BUTTON_HEIGHT + BUTTON_GAP;
		return new FeedbackLayout(top, explanation, afterExplanation, counter, categories, send, send + BUTTON_HEIGHT);
	}
}
