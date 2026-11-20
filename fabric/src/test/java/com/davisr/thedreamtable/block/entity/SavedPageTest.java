package com.davisr.thedreamtable.block.entity;

import com.davisr.thedreamtable.screen.DreamTableScreenHandler;
import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Farm Info shares id 21 with the removed Alerts page, so it is saved as My Farms' work page. How It Works on My
 * Schematics is saved as My Schematics. Every other page saves as it is.
 */
class SavedPageTest {
	@Test
	void farmInfoIsSavedAsTheWorkPage() {
		assertEquals(DreamTableScreenHandler.PAGE_WORK, TableSession.savedPage(DreamTableScreenHandler.PAGE_FARM_INFO));
	}

	@Test
	void schemesHelpIsSavedAsMySchematics() {
		assertEquals(DreamTableScreenHandler.PAGE_SCHEMES, TableSession.savedPage(DreamTableScreenHandler.PAGE_SCHEMES_HELP));
	}

	@Test
	void everyOtherPageIsSavedAsItIs() {
		IntStream.rangeClosed(0, 40)
				.filter(page -> page != DreamTableScreenHandler.PAGE_FARM_INFO && page != DreamTableScreenHandler.PAGE_SCHEMES_HELP)
				.forEach(page -> assertEquals(page, TableSession.savedPage(page), "page " + page));
	}
}
