package com.davisr.thedreamtable.block.entity;

import com.davisr.thedreamtable.block.entity.SessionBuild.LegacyDecision;
import com.davisr.thedreamtable.farm.FarmAreas;
import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyDoneTest {
	private static FarmAreas.Presence presence(int percent, boolean unloaded) {
		return new FarmAreas.Presence(percent, 0, 0, unloaded, Map.of());
	}

	@Test
	void aStandingFarmWithoutARecordIsRecorded() {
		assertEquals(LegacyDecision.RECORD, SessionBuild.legacyDecision(false, presence(100, false)));
		assertEquals(LegacyDecision.RECORD, SessionBuild.legacyDecision(false, presence(50, false)));
	}

	@Test
	void aTakenDownFarmIsNotResurrected() {
		assertEquals(LegacyDecision.SKIP, SessionBuild.legacyDecision(false, presence(0, false)));
		assertEquals(LegacyDecision.SKIP, SessionBuild.legacyDecision(false, presence(49, false)));
	}

	@Test
	void anExistingRecordIsNeverDuplicated() {
		assertEquals(LegacyDecision.SKIP, SessionBuild.legacyDecision(true, presence(100, false)));
	}

	@Test
	void unloadedPartsDeferTheDecision() {
		assertEquals(LegacyDecision.WAIT, SessionBuild.legacyDecision(false, presence(100, true)));
	}

	@Test
	void onlyAnUnflaggedDoneSessionIsLegacy() {
		NbtCompound nbt = new NbtCompound();
		nbt.putString("Phase", DreamTableBlockEntity.PHASE_DONE);
		BuildProgress loaded = new BuildProgress();
		loaded.read(nbt);
		assertTrue(loaded.legacyDone());
		assertTrue(loaded.finishedRecorded());
		loaded.settleLegacy();
		assertFalse(loaded.legacyDone());

		nbt.putBoolean("FinishedRecorded", true);
		loaded.read(nbt);
		assertFalse(loaded.legacyDone());

		NbtCompound building = new NbtCompound();
		building.putString("Phase", DreamTableBlockEntity.PHASE_BUILDING);
		loaded.read(building);
		assertFalse(loaded.legacyDone());
	}
}
