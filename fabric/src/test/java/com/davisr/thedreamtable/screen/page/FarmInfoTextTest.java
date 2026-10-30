package com.davisr.thedreamtable.screen.page;

import com.davisr.thedreamtable.GameSetup;
import com.davisr.thedreamtable.farm.FarmInfo;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.davisr.thedreamtable.screen.ui.Theme.ACCENT;
import static com.davisr.thedreamtable.screen.ui.Theme.CARD_WIDTH;
import static com.davisr.thedreamtable.screen.ui.Theme.NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FarmInfoTextTest {
	/** One pixel per character, cut at the width, so the tests need no font. */
	private static List<OrderedText> wrap(Text text, int width) {
		String string = text.getString();
		List<OrderedText> lines = new ArrayList<>();
		for (int start = 0; start < string.length(); start += width) {
			lines.add(OrderedText.styledForwardsVisitedString(string.substring(start, Math.min(string.length(), start + width)), Style.EMPTY));
		}
		return lines;
	}

	private static String plain(OrderedText text) {
		StringBuilder builder = new StringBuilder();
		text.accept((index, style, codePoint) -> {
			builder.appendCodePoint(codePoint);
			return true;
		});
		return builder.toString();
	}

	@BeforeAll
	static void bootstrap() {
		GameSetup.bootstrap();
	}

	@Test
	void headingsUseTheAccentColorAndBulletsStartWithAMark() {
		FarmInfoText.Layout layout = FarmInfoText.layout(List.of(
				new FarmInfo.Block(FarmInfo.Section.HOW, List.of("One", "Two")),
				new FarmInfo.Block(FarmInfo.Section.TIPS, List.of("Three"))), 168, FarmInfoTextTest::wrap);
		List<FarmInfoText.Row> rows = layout.rows();
		assertEquals(5, rows.size());
		assertEquals(ACCENT, rows.get(0).color());
		assertEquals("gui.the_dream_table.info_how", plain(rows.get(0).text()));
		assertEquals(0, rows.get(0).indent());
		assertEquals(NAME, rows.get(1).color());
		assertTrue(rows.get(1).bullet());
		assertEquals("One", plain(rows.get(1).text()));
		assertEquals(FarmInfoText.BULLET_INDENT, rows.get(1).indent());
		assertEquals(ACCENT, rows.get(3).color());
		assertEquals("gui.the_dream_table.info_tips", plain(rows.get(3).text()));
	}

	@Test
	void longBulletsWrapInsideTheCardWithOnlyTheFirstLineMarked() {
		String longText = "x".repeat(CARD_WIDTH - FarmInfoText.BULLET_INDENT + 5);
		FarmInfoText.Layout layout = FarmInfoText.layout(List.of(new FarmInfo.Block(FarmInfo.Section.WORKS, List.of(longText))), 168, FarmInfoTextTest::wrap);
		List<FarmInfoText.Row> rows = layout.rows();
		assertEquals(3, rows.size());
		assertTrue(rows.get(1).bullet());
		assertFalse(rows.get(2).bullet());
		assertEquals(CARD_WIDTH - FarmInfoText.BULLET_INDENT, plain(rows.get(1).text()).length());
		assertEquals(5, plain(rows.get(2).text()).length());
		assertEquals(rows.get(1).indent(), rows.get(2).indent());
	}

	@Test
	void rowsStackWithoutOverlapAndSectionsAreSeparated() {
		FarmInfoText.Layout layout = FarmInfoText.layout(List.of(
				new FarmInfo.Block(FarmInfo.Section.HOW, List.of("One")),
				new FarmInfo.Block(FarmInfo.Section.OUTPUT, List.of("Two"))), 168, FarmInfoTextTest::wrap);
		List<FarmInfoText.Row> rows = layout.rows();
		assertEquals(0, rows.get(0).top());
		assertEquals(FarmInfoText.LINE_HEIGHT, rows.get(1).top());
		assertEquals(2 * FarmInfoText.LINE_HEIGHT + FarmInfoText.SECTION_GAP, rows.get(2).top());
		assertEquals(4 * FarmInfoText.LINE_HEIGHT + FarmInfoText.SECTION_GAP, layout.height());
	}

	@Test
	void noSectionsMakeAnEmptyLayout() {
		FarmInfoText.Layout layout = FarmInfoText.layout(List.of(), 168, FarmInfoTextTest::wrap);
		assertTrue(layout.rows().isEmpty());
		assertEquals(0, layout.height());
	}
}
