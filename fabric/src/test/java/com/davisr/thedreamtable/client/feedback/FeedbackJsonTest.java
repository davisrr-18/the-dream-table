package com.davisr.thedreamtable.client.feedback;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The feedback form sends only this body, so its shape, cleaning, escaping and limits are pinned here. */
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
	void newlinesAndTabsAreEscapedOrRemoved() {
		String json = body("line one\nline two\tend");
		assertTrue(json.contains("\"text\":\"line one\\nline twoend\""), json);
		assertFalse(json.contains("\n"), "a raw newline would break the JSON body");
	}

	@Test
	void jsonEscapesEveryControlCharacterAsUnicode() {
		assertEquals("\"\\u0007\"", FeedbackJson.quote("\u0007"));
		assertEquals("\"\\u001f\"", FeedbackJson.quote("\u001f"));
	}

	@Test
	void controlCharactersAreRemovedBeforeCounting() {
		assertTrue(body("bell\u0007 rings here").contains("\"text\":\"bell rings here\""));
		assertEquals("abcdef\nghi", FeedbackJson.cleanText("abc\u0007def\r\nghi\r\t"));
	}

	@Test
	void unicodeTextIsKeptAsIs() {
		String json = body("A\u00e7\u00e3o \u2714 \u6f22\u5b57 \ud83d\ude00 done");
		assertTrue(json.contains("A\u00e7\u00e3o \u2714 \u6f22\u5b57 \ud83d\ude00 done"), json);
	}

	@Test
	void unpairedSurrogateBecomesReplacementCharacter() {
		String json = body("broken \ud800 surrogate");
		assertTrue(json.contains("broken \uFFFD surrogate"), json);
	}

	@Test
	void windowsLineBreaksBecomeOneNewline() {
		assertEquals("first line\nsecond line", FeedbackJson.cleanText("first line\r\nsecond line"));
		assertTrue(body("first line\r\nsecond line").contains("first line\\nsecond line"));
	}

	@Test
	void whitespaceAtTheEdgesIsTrimmedLikeJavaScript() {
		assertEquals("padded text", FeedbackJson.cleanText("   padded text \n"));
		assertEquals("abcdefghij", FeedbackJson.cleanText("\u2028abcdefghij\u2029"));
		assertEquals("abcdefghij", FeedbackJson.cleanText("\uFEFFabcdefghij\u00A0"));
	}

	@Test
	void nonBreakingAndZeroWidthSpacesDoNotCount() {
		assertThrows(IllegalArgumentException.class, () -> body("\u00A0".repeat(10)));
		assertThrows(IllegalArgumentException.class, () -> body("\u200B".repeat(10)));
		assertThrows(IllegalArgumentException.class, () -> body("a\u200Bbcdefghi"));
		assertFalse(FeedbackJson.validText(FeedbackJson.cleanText("\u2007\u202F".repeat(5))));
	}

	@Test
	void nineVisibleCharactersInsideSpacesAreTooShort() {
		assertThrows(IllegalArgumentException.class, () -> body(" abcdefghi "));
		assertTrue(body("abcdefghij").contains("\"text\":\"abcdefghij\""));
	}

	@Test
	void textNeedsAtLeastTenCharactersAndAtMostTwoThousand() {
		assertThrows(IllegalArgumentException.class, () -> body("123456789"));
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
		assertEquals("a__o", FeedbackJson.field("a\u00e7\u00e3o"));
		assertTrue(FeedbackJson.body("long enough text", FeedbackCategory.IDEA, "1.5.0", "bad loader", "1.21.1")
				.contains("\"loader\":\"bad_loader\""));
	}

	@Test
	void emptyVersionFieldBecomesUnknownAndLongOneIsCut() {
		assertEquals("unknown", FeedbackJson.field(""));
		assertEquals("x".repeat(32), FeedbackJson.field("x".repeat(40)));
	}
}
