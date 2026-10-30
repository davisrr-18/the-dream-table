package com.davisr.thedreamtable.screen.page;

import com.davisr.thedreamtable.GameSetup;
import com.davisr.thedreamtable.farm.FarmOptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.davisr.thedreamtable.screen.DreamTableScreenHandler.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageRoutesTest {
	@BeforeAll
	static void bootstrap() {
		GameSetup.bootstrap();
	}

	@Test
	void categoryRouteCountTracksTheFarmCategoryEnum() {
		assertEquals(FarmOptions.Category.values().length, PageRoutes.CATEGORY_PAGE_COUNT);
	}

	@Test
	void classifiesEveryExistingPage() {
		assertEquals(PrimaryTab.BUILD, PageRoutes.tabFor(PAGE_SELECT));
		assertEquals(PrimaryTab.BUILD, PageRoutes.tabFor(PAGE_WORK));
		assertEquals(PrimaryTab.BUILD, PageRoutes.tabFor(PAGE_REQUIREMENTS));
		assertEquals(PrimaryTab.SCHEMATICS, PageRoutes.tabFor(PAGE_SCHEMES));
		assertEquals(PrimaryTab.SETTINGS, PageRoutes.tabFor(PAGE_OPTIONS));
		assertEquals(PrimaryTab.BUILD, PageRoutes.tabFor(PAGE_MATERIALS));
		assertEquals(PrimaryTab.BUILD, PageRoutes.tabFor(PAGE_SWAP));
		assertEquals(PrimaryTab.SETTINGS, PageRoutes.tabFor(PAGE_SPEED));
		assertEquals(PrimaryTab.FARMS, PageRoutes.tabFor(PAGE_LOADED));
		assertEquals(PrimaryTab.FARMS, PageRoutes.tabFor(PAGE_FARM));
		assertEquals(PrimaryTab.FARMS, PageRoutes.tabFor(PAGE_INVITES));
		assertEquals(PrimaryTab.BUILD, PageRoutes.tabFor(PAGE_FARM_INFO));
		for (int index = 0; index < PageRoutes.CATEGORY_PAGE_COUNT; index++) {
			assertEquals(PrimaryTab.BUILD, PageRoutes.tabFor(PAGE_CATEGORY + index));
		}
	}

	@Test
	void resolvesPrimaryRootsAndActiveBuildWork() {
		assertEquals(PAGE_SELECT, PageRoutes.rootFor(PrimaryTab.BUILD, false));
		assertEquals(PAGE_WORK, PageRoutes.rootFor(PrimaryTab.BUILD, true));
		assertEquals(PAGE_LOADED, PageRoutes.rootFor(PrimaryTab.FARMS, false));
		assertEquals(PAGE_SCHEMES, PageRoutes.rootFor(PrimaryTab.SCHEMATICS, false));
		assertEquals(PAGE_OPTIONS, PageRoutes.rootFor(PrimaryTab.SETTINGS, false));
	}

	@Test
	void resolvesOneDeterministicParentLevel() {
		assertEquals(PAGE_SELECT, PageRoutes.parentOf(PAGE_WORK));
		assertEquals(PAGE_WORK, PageRoutes.parentOf(PAGE_REQUIREMENTS));
		assertEquals(PAGE_WORK, PageRoutes.parentOf(PAGE_MATERIALS));
		assertEquals(PAGE_WORK, PageRoutes.parentOf(PAGE_FARM_INFO));
		assertEquals(PAGE_MATERIALS, PageRoutes.parentOf(PAGE_SWAP));
		assertEquals(PAGE_OPTIONS, PageRoutes.parentOf(PAGE_SPEED));
		assertEquals(PAGE_LOADED, PageRoutes.parentOf(PAGE_FARM));
		assertEquals(PAGE_LOADED, PageRoutes.parentOf(PAGE_INVITES));
		for (int index = 0; index < PageRoutes.CATEGORY_PAGE_COUNT; index++) {
			assertEquals(PAGE_SELECT, PageRoutes.parentOf(PAGE_CATEGORY + index));
		}
	}

	@Test
	void identifiesOnlyPrimaryRootsAsRoots() {
		assertTrue(PageRoutes.isRoot(PAGE_SELECT));
		assertTrue(PageRoutes.isRoot(PAGE_SCHEMES));
		assertTrue(PageRoutes.isRoot(PAGE_OPTIONS));
		assertTrue(PageRoutes.isRoot(PAGE_LOADED));
		assertFalse(PageRoutes.isRoot(PAGE_WORK));
		assertFalse(PageRoutes.isRoot(PAGE_FARM));
		assertFalse(PageRoutes.isRoot(PAGE_INVITES));
		assertFalse(PageRoutes.isRoot(PAGE_FARM_INFO));
	}

	@Test
	void rejectsUnregisteredPagesInsteadOfSilentlyChoosingATab() {
		assertThrows(IllegalArgumentException.class, () -> PageRoutes.tabFor(999));
	}
}
