package com.davisr.thedreamtable.client;

import com.davisr.thedreamtable.GameSetup;
import com.davisr.thedreamtable.network.TableNoticePayload.Severity;
import net.minecraft.text.Text;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NoticeBoardTest {
	@BeforeAll
	static void bootstrap() {
		GameSetup.bootstrap();
	}

	@Test
	void theNewestNoticeReplacesTheOlderOne() {
		NoticeBoard board = new NoticeBoard();
		Text second = Text.literal("second");
		board.show(Text.literal("first"), Severity.INFO, 1000);
		board.show(second, Severity.PROBLEM, 2000);

		assertSame(second, board.current(2500).text());
		assertEquals(Severity.PROBLEM, board.current(2500).severity());
	}

	@Test
	void aNoticeStaysFiveSecondsAndThenIsGone() {
		NoticeBoard board = new NoticeBoard();
		board.show(Text.literal("x"), Severity.INFO, 1000);

		assertEquals(Severity.INFO, board.current(1000 + NoticeBoard.LIFETIME_MS - 1).severity());
		assertNull(board.current(1000 + NoticeBoard.LIFETIME_MS));
		assertNull(board.current(1000), "an expired notice does not come back");
	}

	@Test
	void aReplacementRestartsTheTimer() {
		NoticeBoard board = new NoticeBoard();
		board.show(Text.literal("a"), Severity.INFO, 0);
		board.show(Text.literal("a"), Severity.INFO, 4000);

		assertEquals("a", board.current(8000).text().getString());
	}

	@Test
	void theNoticeIsOpaqueThenFadesOverItsLastSecond() {
		assertEquals(1.0F, NoticeBoard.alpha(0));
		assertEquals(1.0F, NoticeBoard.alpha(NoticeBoard.LIFETIME_MS - NoticeBoard.FADE_MS));
		assertEquals(0.5F, NoticeBoard.alpha(NoticeBoard.LIFETIME_MS - NoticeBoard.FADE_MS / 2), 0.001F);
		assertEquals(0.0F, NoticeBoard.alpha(NoticeBoard.LIFETIME_MS));
		assertTrue(NoticeBoard.alpha(4500) > NoticeBoard.alpha(4900));
	}

	@Test
	void clearingDropsTheNotice() {
		NoticeBoard board = new NoticeBoard();
		board.show(Text.literal("x"), Severity.INFO, 0);
		board.clear();
		assertNull(board.current(1));
	}

	@Test
	void aTemporaryNoticeShowsOverThePersistentLineAndThenItComesBack() {
		NoticeBoard board = new NoticeBoard();
		Text progress = Text.literal("Building 1/9");
		board.setPersistent(progress, Severity.INFO);
		assertSame(progress, board.current(10).text());

		board.show(Text.literal("Paused"), Severity.PROBLEM, 1000);
		assertEquals("Paused", board.current(1500).text().getString());
		assertSame(progress, board.current(1000 + NoticeBoard.LIFETIME_MS).text());
	}

	@Test
	void thePersistentLineNeverExpiresOrFades() {
		NoticeBoard board = new NoticeBoard();
		board.setPersistent(Text.literal("x"), Severity.INFO);

		NoticeBoard.Notice line = board.current(10_000_000);
		assertEquals(1.0F, line.alpha(10_000_000));
		assertTrue(line.persistent());
	}

	@Test
	void thePersistentLineIsReplacedInPlaceAndRemovedWithNull() {
		NoticeBoard board = new NoticeBoard();
		board.setPersistent(Text.literal("Building 1/9"), Severity.INFO);
		board.setPersistent(Text.literal("Building 2/9"), Severity.INFO);
		assertEquals("Building 2/9", board.current(0).text().getString());

		board.setPersistent(null, Severity.INFO);
		assertNull(board.current(0));
	}

	@Test
	void theMenuLeavesThePersistentLineOutButStillShowsOrdinaryNotices() {
		NoticeBoard board = new NoticeBoard();
		board.setPersistent(Text.literal("Building 1/9"), Severity.INFO);
		assertNull(board.current(0, false));

		board.show(Text.literal("hello"), Severity.INFO, 0);
		assertEquals("hello", board.current(1, false).text().getString());
	}

	@Test
	void clearingAlsoDropsThePersistentLine() {
		NoticeBoard board = new NoticeBoard();
		board.setPersistent(Text.literal("x"), Severity.INFO);
		board.clear();
		assertNull(board.current(0));
	}
}
