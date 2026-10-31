package com.davisr.thedreamtable.block.entity;

import com.davisr.thedreamtable.farm.FarmGeometry;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import static com.davisr.thedreamtable.block.entity.TableSession.ParkKind.BUILD;
import static com.davisr.thedreamtable.block.entity.TableSession.ParkKind.NOTHING;
import static com.davisr.thedreamtable.block.entity.TableSession.ParkKind.PROJECT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What a broken table keeps of each session, and where a build kept that way may go on. */
class ParkRulesTest {
	@Test
	void anUnusedSessionKeepsNothing() {
		assertEquals(NOTHING, TableSession.parkKind(true, false, false, false, false, false));
	}

	@Test
	void aFinishedFarmKeepsOnlyLeftoverMaterials() {
		assertEquals(NOTHING, TableSession.parkKind(false, true, false, false, false, true));
		assertEquals(PROJECT, TableSession.parkKind(false, true, true, false, false, true));
	}

	@Test
	void aStartedBuildWithASpotIsKeptAsABuild() {
		assertEquals(BUILD, TableSession.parkKind(false, false, true, false, true, true));
		assertEquals(BUILD, TableSession.parkKind(false, false, false, false, true, true));
	}

	@Test
	void aChosenOrPlacedFarmNotStartedKeepsTheFarmAndMaterials() {
		assertEquals(PROJECT, TableSession.parkKind(false, false, false, false, false, false));
		assertEquals(PROJECT, TableSession.parkKind(false, false, true, false, false, true));
	}

	@Test
	void aBuildWithoutASpotFromOldWorldsKeepsTheFarmAndMaterials() {
		assertEquals(PROJECT, TableSession.parkKind(false, false, true, false, true, false));
	}

	@Test
	void aRepairEndsAndKeepsOnlyTheFarmAndMaterials() {
		assertEquals(PROJECT, TableSession.parkKind(false, false, true, true, true, true));
	}

	@Test
	void reachIsTheDistanceATablePlacesFarmsAt() {
		BlockPos table = new BlockPos(0, 64, 0);
		assertTrue(FarmGeometry.withinReach(table, table.add(FarmGeometry.AIM_RANGE, 0, 0)));
		assertFalse(FarmGeometry.withinReach(table, table.add(FarmGeometry.AIM_RANGE + 1, 0, 0)));
		assertFalse(FarmGeometry.withinReach(table, table.add(40, 0, 40)));
	}

	@Test
	void aMovedBuildGoesOnOnlyInItsOwnWorldAndWithinReach() {
		assertNull(TableSession.movedBuildBlocker(true, true));
		assertEquals("gui.the_dream_table.moved_build_too_far", TableSession.movedBuildBlocker(true, false));
		assertEquals("gui.the_dream_table.moved_build_other_world", TableSession.movedBuildBlocker(false, true));
		assertEquals("gui.the_dream_table.moved_build_other_world", TableSession.movedBuildBlocker(false, false));
	}
}
