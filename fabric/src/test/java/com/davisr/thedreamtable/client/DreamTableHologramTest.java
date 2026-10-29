package com.davisr.thedreamtable.client;

import com.davisr.thedreamtable.client.DreamTableHologram.TableView;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DreamTableHologramTest {
	private static final BlockPos TABLE = new BlockPos(10, 64, 10);
	private static final BlockPos ANCHOR = new BlockPos(14, 64, 12);
	private static final String FARM = "iron_farm";

	@AfterEach
	void reset() {
		DreamTableHologram.clear();
	}

	@Test
	void anUnloadedChunkIsAwayNotGone() {
		assertEquals(TableView.AWAY, DreamTableHologram.viewOf(false, false), "an unloaded chunk reads as air");
		assertEquals(TableView.AWAY, DreamTableHologram.viewOf(false, true));
		assertEquals(TableView.STANDING, DreamTableHologram.viewOf(true, true));
		assertEquals(TableView.GONE, DreamTableHologram.viewOf(true, false));
	}

	@Test
	void aPlacedFarmSurvivesWalkingAwayAndBack() {
		DreamTableHologram.show(TABLE, FARM, 0, false);
		DreamTableHologram.anchor(ANCHOR);

		assertFalse(DreamTableHologram.settle(TableView.AWAY));
		assertFalse(DreamTableHologram.settle(TableView.AWAY));
		assertTrue(DreamTableHologram.hasPlacement());
		assertEquals(ANCHOR, DreamTableHologram.anchorForBuild());
		assertEquals(TABLE, DreamTableHologram.trackedTable());

		assertTrue(DreamTableHologram.settle(TableView.STANDING));
		assertTrue(DreamTableHologram.hasPlacement());
	}

	@Test
	void aBuildInProgressKeepsItsStateWhileTheTableIsAway() {
		DreamTableHologram.show(TABLE, FARM, 42, true);
		DreamTableHologram.anchor(ANCHOR);

		DreamTableHologram.settle(TableView.AWAY);
		// The screen shows the table again with the same farm: nothing is reset.
		DreamTableHologram.show(TABLE, FARM, 57, false);

		assertTrue(DreamTableHologram.hasPlacement());
		assertEquals(ANCHOR, DreamTableHologram.anchorForBuild());
	}

	@Test
	void aBrokenTableForgetsTheFarm() {
		DreamTableHologram.show(TABLE, FARM, 0, false);
		DreamTableHologram.anchor(ANCHOR);

		assertFalse(DreamTableHologram.settle(TableView.GONE));
		assertFalse(DreamTableHologram.hasPlacement());
		assertNull(DreamTableHologram.trackedTable());
	}

	@Test
	void choosingAnotherFarmStartsOver() {
		DreamTableHologram.show(TABLE, FARM, 0, false);
		DreamTableHologram.anchor(ANCHOR);

		DreamTableHologram.show(TABLE, "sugar_cane_farm", 0, false);

		assertFalse(DreamTableHologram.hasPlacement());
		assertEquals(TABLE, DreamTableHologram.trackedTable());
	}

	@Test
	void anotherTableForSameFarmStartsOver() {
		DreamTableHologram.show(TABLE, FARM, 0, false);
		DreamTableHologram.anchor(ANCHOR);

		DreamTableHologram.show(TABLE.east(), FARM, 0, false);

		assertFalse(DreamTableHologram.hasPlacement());
		assertEquals(TABLE.east(), DreamTableHologram.trackedTable());
	}

	@Test
	void theSameTableKeepsItsPlacementWhenShownAgain() {
		DreamTableHologram.show(TABLE, FARM, 0, false);
		DreamTableHologram.anchor(ANCHOR);

		DreamTableHologram.show(TABLE, FARM, 3, false);

		assertTrue(DreamTableHologram.hasPlacement());
	}

	@Test
	void layerToggleNeedsAFollowedFarmAndClearResetsIt() {
		assertFalse(DreamTableHologram.flipLayers(), "no table, nothing to toggle");
		assertFalse(DreamTableHologram.allLayers());

		DreamTableHologram.show(TABLE, FARM, 5, false);
		assertTrue(DreamTableHologram.flipLayers());
		assertTrue(DreamTableHologram.allLayers());

		DreamTableHologram.clear();
		assertFalse(DreamTableHologram.allLayers(), "a new farm starts on the next layers only");
		assertFalse(DreamTableHologram.flipLayers());
		assertFalse(DreamTableHologram.allLayers());
	}
}
