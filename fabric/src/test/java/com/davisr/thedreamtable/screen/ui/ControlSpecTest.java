package com.davisr.thedreamtable.screen.ui;

import com.davisr.thedreamtable.GameSetup;
import com.davisr.thedreamtable.screen.ui.layout.UiRect;
import net.minecraft.text.Text;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControlSpecTest {
	private static final String UNAVAILABLE_KEY = "gui.the_dream_table.narration.disabled";

	@BeforeAll
	static void bootstrap() {
		GameSetup.bootstrap();
	}

	private static ControlSpec spec(boolean enabled, Text reason) {
		return new ControlSpec(new ControlId("page.1.build"), ControlGroup.CONTENT, new UiRect(0, 0, 168, 20),
				Text.literal("Build"), Text.empty(), enabled, false, reason, 0, 0);
	}

	private static int count(String text, String part) {
		int count = 0;
		for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + part.length())) {
			count++;
		}
		return count;
	}

	@Test
	void aDisabledControlIsReadAsUnavailableOnce() {
		String unavailable = Text.translatable(UNAVAILABLE_KEY).getString();

		// Page controls pass no reason of their own; the generic word comes from the spec.
		assertEquals(1, count(spec(false, Text.empty()).narration().getString(), unavailable));
		// A reason that is only the generic word must not double it.
		assertEquals(1, count(spec(false, Text.translatable(UNAVAILABLE_KEY)).narration().getString(), unavailable));
	}

	@Test
	void aRealReasonIsReadAfterTheUnavailableWord() {
		String narration = spec(false, Text.literal("Needs a table")).narration().getString();

		assertTrue(narration.endsWith("Needs a table"));
		assertEquals(1, count(narration, Text.translatable(UNAVAILABLE_KEY).getString()));
	}

	@Test
	void anEnabledControlIsNotUnavailable() {
		assertEquals("Build", spec(true, Text.empty()).narration().getString());
	}
}
