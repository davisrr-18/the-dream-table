package com.davisr.thedreamtable.client.feedback;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The request body is the only thing the feedback form sends, so its shape, escaping and limits are pinned here. */
class FeedbackJsonTest {
	private static String body(String text) {
		return FeedbackJson.body(text, FeedbackCategory.BUG, "1.5.0", "fabric", "1.21.1");
	}

	@Test
	void bodyHasExactlyTheFiveFieldsInOrder() {
		assertEquals("{\"text\":\"Something is broken here\",\"category\":\"bug\",\"modVersion\":\"1.5.0\","
				+ "\"loader\":\"fabric\",\"mcVersion\":\"1.21.1\"}", body("Something is broken here"));
	}

	@Test
	void quotesAndBackslashesAreEscaped() {
		String json = body("say \"hi\" \\ path ok");
		assertTrue(json.contains("\"text\":\"say \\\"hi\\\" \\\\ path ok\""), json);
	}

	@Test
	void newlinesAndTabsAreEscaped() {
		String json = body("line one\nline two\tend");
		assertTrue(json.contains("\"text\":\"line one\\nline two\\tend\""), json);
		assertFalse(json.contains("\n"), "a raw newline would break the JSON body");
	}

	@Test
	void controlCharactersBecomeUnicodeEscapes() {
		String json = body("bell\u0007 rings here");
		assertTrue(json.contains("bell\\u0007 rings here"), json);
	}

	@Test
	void unicodeTextIsKeptAsIs() {
		String json = body("Ação ✔ 漢字 😀 done");
		assertTrue(json.contains("Ação ✔ 漢字 😀 done"), json);
	}

	@Test
	void unpairedSurrogateBecomesReplacementCharacter() {
		String json = body("broken \ud800 surrogate");
		assertTrue(json.contains("broken � surrogate"), json);
	}

	@Test
	void windowsLineBreaksBecomeOneNewline() {
		String cleaned = FeedbackJson.cleanText("first line\r\nsecond line");
		assertEquals("first line\nsecond line", cleaned);
		assertTrue(body("first line\r\nsecond line").contains("first line\\nsecond line"));
	}

	@Test
	void surroundingWhitespaceIsNotCounted() {
		assertEquals("padded text", FeedbackJson.cleanText("   padded text \n"));
		assertTrue(body("   padded text   ").contains("\"text\":\"padded text\""));
	}

	@Test
	void textNeedsAtLeastTenCharactersAndAtMostTwoThousand() {
		assertThrows(IllegalArgumentException.class, () -> body("123456789"));
		assertThrows(IllegalArgumentException.class, () -> body("         "));
		assertEquals(10, FeedbackJson.cleanText("1234567890").length());
		assertTrue(FeedbackJson.validText("a".repeat(10)));
		assertTrue(FeedbackJson.validText("a".repeat(2000)));
		assertFalse(FeedbackJson.validText("a".repeat(2001)));
		assertThrows(IllegalArgumentException.class, () -> body("a".repeat(2001)));
	}

	@Test
	void categoryWireNamesAreFixed() {
		assertEquals("bug", FeedbackCategory.BUG.wire());
		assertEquals("idea", FeedbackCategory.IDEA.wire());
		assertEquals("other", FeedbackCategory.OTHER.wire());
		assertEquals(3, FeedbackCategory.values().length);
	}

	@Test
	void versionFieldsKeepTheirCharacters() {
		assertEquals("1.5.0", FeedbackJson.field("1.5.0"));
		assertEquals("1.21.1+fabric_0.16", FeedbackJson.field("1.21.1+fabric_0.16"));
	}

	@Test
	void otherCharactersInVersionFieldsBecomeUnderscores() {
		assertEquals("a_b_c_d", FeedbackJson.field("a b/c\"d"));
		assertEquals("a__o", FeedbackJson.field("ação"));
		assertTrue(body("long enough text").contains("\"modVersion\":\"1.5.0\""));
		assertTrue(FeedbackJson.body("long enough text", FeedbackCategory.IDEA, "1.5.0", "bad loader", "1.21.1")
				.contains("\"loader\":\"bad_loader\""));
	}

	@Test
	void emptyVersionFieldBecomesUnknownAndLongOneIsCut() {
		assertEquals("unknown", FeedbackJson.field(""));
		assertEquals("x".repeat(32), FeedbackJson.field("x".repeat(40)));
	}
}
