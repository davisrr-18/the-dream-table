package com.davisr.thedreamtable.farm;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FarmListingTest {
	private static final String OVERWORLD = "minecraft:overworld";
	private static final String NETHER = "minecraft:the_nether";

	/** Newest first, as the server sends them. */
	private static final List<FarmLoader.Entry> FARMS = List.of(
			farm("Sugar Cane", "River Cane", FarmRole.OWNER, false, OVERWORLD, new BlockPos(500, 64, 0), FarmStorage.RUNNING, 0, true, 40),
			farm("Iron Farm", "", FarmRole.OPERATOR, true, NETHER, new BlockPos(10, 64, 10), FarmStorage.FULL, 0, true, 100),
			farm("Bamboo", "", FarmRole.VIEWER, false, OVERWORLD, new BlockPos(20, 64, 0), FarmStorage.UNLOADED, FarmLoader.UNKNOWN, true, FarmLoader.UNKNOWN),
			farm("Crop Farm", "", FarmRole.OWNER, true, OVERWORLD, new BlockPos(100, 64, 0), FarmStorage.RUNNING, 3, true, 10),
			farm("Old Farm", "", FarmRole.OWNER, false, OVERWORLD, new BlockPos(900, 64, 0), FarmStorage.UNLOADED, FarmLoader.UNKNOWN, false, FarmLoader.UNKNOWN));
	private static final FarmListing.Viewer HOME = new FarmListing.Viewer(OVERWORLD, new BlockPos(0, 64, 0));

	private static FarmLoader.Entry farm(String name, String customName, FarmRole role, boolean favorite, String dimension, BlockPos where,
			int status, int missing, boolean available, int storage) {
		return new FarmLoader.Entry(name, name, "", customName, dimension, where, role, favorite, false,
				false, false, status, missing, available, storage);
	}

	@Test
	void aSavedDesignIsUsedForTheExactFarmPreview() {
		FarmLoader.Entry farm = new FarmLoader.Entry("key", "base:farm", "saved:base:farm#1@unique", "", "minecraft:overworld", BlockPos.ORIGIN,
				FarmRole.OWNER, false, false, false, false, FarmStorage.RUNNING, 2, true, 0);

		assertEquals("saved:base:farm#1@unique", farm.designId());
	}

	private static List<Integer> shown(String query, FarmListing.Show show, String dimension, FarmListing.Sort sort) {
		return FarmListing.shown(FARMS, FarmLoader.Entry::displayName, query, show, dimension, sort, HOME);
	}

	@Test
	void everythingNewestFirstByDefault() {
		assertEquals(List.of(0, 1, 2, 3, 4), shown("", FarmListing.Show.ALL, "", FarmListing.Sort.NEWEST));
	}

	@Test
	void searchMatchesPartOfTheNameIgnoringCase() {
		assertEquals(List.of(1, 3, 4), shown("  ar ", FarmListing.Show.ALL, "", FarmListing.Sort.NEWEST));
		assertEquals(List.of(0), shown("river", FarmListing.Show.ALL, "", FarmListing.Sort.NEWEST));
		assertEquals(List.of(2), shown("BAMB", FarmListing.Show.ALL, "", FarmListing.Sort.NEWEST));
	}

	@Test
	void attentionMeansFullMissingOrDesignLost() {
		assertEquals(List.of(1, 3, 4), shown("", FarmListing.Show.ATTENTION, "", FarmListing.Sort.NEWEST));
		assertEquals(List.of(0, 3), shown("", FarmListing.Show.RUNNING, "", FarmListing.Sort.NEWEST));
		assertEquals(List.of(2), shown("", FarmListing.Show.UNLOADED, "", FarmListing.Sort.NEWEST));
		assertEquals(List.of(1), shown("", FarmListing.Show.FULL, "", FarmListing.Sort.NEWEST));
	}

	@Test
	void accessAndFavoriteFiltersUseServerDerivedSnapshotFields() {
		assertEquals(List.of(0, 3, 4), shown("", FarmListing.Show.OWNED, "", FarmListing.Sort.NEWEST));
		assertEquals(List.of(1, 2), shown("", FarmListing.Show.SHARED, "", FarmListing.Sort.NEWEST));
	}

	@Test
	void favoritesHaveTheirOwnFilterInsteadOfBeingHiddenInTheShowCycle() {
		assertFalse(Arrays.stream(FarmListing.Show.values()).anyMatch(show -> show.name().equals("FAVORITES")));
		assertTrue(Arrays.stream(FarmListing.Sort.values()).anyMatch(sort -> sort.name().equals("FAVORITES")));
	}

	@Test
	void favoriteFilterAndSortUseTheSnapshotFavoriteFlag() {
		assertEquals(List.of(1, 3), FarmListing.shown(FARMS, FarmLoader.Entry::displayName, "", FarmListing.Show.ALL,
				true, "", FarmListing.Sort.NEWEST, HOME));
		assertEquals(List.of(1, 3, 0, 2, 4), shown("", FarmListing.Show.ALL, "", FarmListing.Sort.FAVORITES));
	}

	@Test
	void keepLoadedFilterUsesOnlyTheFarmSnapshotState() {
		FarmLoader.Entry loaded = new FarmLoader.Entry("loaded", "Sugar Cane", "", "", OVERWORLD, BlockPos.ORIGIN,
				FarmRole.OPERATOR, false, false, true, false, FarmStorage.RUNNING, 0, true, 20);
		List<FarmLoader.Entry> farms = List.of(loaded, FARMS.get(0));

		assertEquals(List.of(0), FarmListing.shown(farms, FarmLoader.Entry::displayName, "", FarmListing.Show.KEPT_LOADED,
				"", FarmListing.Sort.NEWEST, HOME));
	}

	@Test
	void dimensionFilter() {
		assertEquals(List.of(1), shown("", FarmListing.Show.ALL, NETHER, FarmListing.Sort.NEWEST));
		assertEquals(List.of(OVERWORLD, NETHER), FarmListing.dimensions(FARMS));
	}

	@Test
	void sorts() {
		assertEquals(List.of(2, 3, 1, 4, 0), shown("", FarmListing.Show.ALL, "", FarmListing.Sort.NAME));
		assertEquals(List.of(3, 1, 4, 0, 2), shown("", FarmListing.Show.ALL, "", FarmListing.Sort.STATUS));
		assertEquals(List.of(1, 0, 3, 2, 4), shown("", FarmListing.Show.ALL, "", FarmListing.Sort.STORAGE));
		assertEquals(List.of(2, 3, 0, 4, 1), shown("", FarmListing.Show.ALL, "", FarmListing.Sort.DISTANCE));
	}

	@Test
	void distanceOnlyInTheSameDimension() {
		assertEquals(100, FarmListing.blocksAway(FARMS.get(3), HOME));
		assertEquals(-1, FarmListing.blocksAway(FARMS.get(1), HOME));
	}

	@Test
	void stableKeyFindsTheSameFarmAfterRefreshReordering() {
		String key = FARMS.get(1).key();
		assertEquals(0, FarmListing.indexOfKey(List.of(FARMS.get(1), FARMS.get(0)), key));
		assertEquals(-1, FarmListing.indexOfKey(List.of(FARMS.get(0)), key));
	}
}
