package com.davisr.thedreamtable.block.entity;

import com.davisr.thedreamtable.farm.BuildPace;
import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildProgressTest {
	private static int placedOver(BuildProgress progress, int rate, int ticks) {
		int total = 0;
		for (int tick = 0; tick < ticks; tick++) {
			total += progress.due(rate);
		}
		return total;
	}

	@Test
	void firstBlockComesRightAwayThenThePaceHolds() {
		BuildProgress progress = new BuildProgress();
		progress.start(UUID.randomUUID(), true, 7L);
		assertEquals(1, progress.due(1));
		assertEquals(1, placedOver(progress, 1, 20));
	}

	@Test
	void fastPacesAreSpreadOverEveryTick() {
		BuildProgress progress = new BuildProgress();
		progress.reset(true);
		for (int tick = 0; tick < 20; tick++) {
			assertEquals(2, progress.due(40));
		}
		assertEquals(30, placedOver(progress, 30, 20));
		assertTrue(Math.abs(64 * 3 - placedOver(progress, 64, 60)) <= 1);
	}

	@Test
	void startingFromScratchForgetsPlacedSteps() {
		BuildProgress progress = new BuildProgress();
		progress.start(UUID.randomUUID(), true, 7L);
		progress.recordPlaced(true);
		progress.advance();
		assertTrue(progress.placed(0));
		assertTrue(progress.paid(0));
		progress.start(UUID.randomUUID(), true, 7L);
		assertFalse(progress.placed(0));
		assertEquals(0, progress.index());
	}

	@Test
	void resumingKeepsPlacedSteps() {
		BuildProgress progress = new BuildProgress();
		progress.start(UUID.randomUUID(), true, 7L);
		progress.recordPlaced(false);
		progress.advance();
		progress.stop(DreamTableBlockEntity.PHASE_PAUSED);
		assertTrue(progress.resumable());
		progress.start(UUID.randomUUID(), false, 7L);
		assertTrue(progress.placed(0));
		assertFalse(progress.paid(0));
		assertEquals(1, progress.index());
	}

	@Test
	void signatureDetectsAChangedFarm() {
		BuildProgress progress = new BuildProgress();
		assertTrue(progress.matches(123L));
		progress.start(UUID.randomUUID(), true, 7L);
		assertTrue(progress.matches(7L));
		assertFalse(progress.matches(8L));
	}

	@Test
	void saveAndLoadKeepsEverything() {
		BuildProgress progress = new BuildProgress();
		UUID owner = UUID.randomUUID();
		progress.setPace(-5);
		progress.start(owner, true, 42L);
		progress.recordPlaced(true);
		progress.advance();
		progress.advance();
		progress.stop(DreamTableBlockEntity.PHASE_NEED);
		NbtCompound nbt = new NbtCompound();
		progress.write(nbt);

		BuildProgress loaded = new BuildProgress();
		loaded.read(nbt);
		assertEquals(DreamTableBlockEntity.PHASE_NEED, loaded.phase());
		assertEquals(2, loaded.index());
		assertEquals(-5, loaded.pace());
		assertEquals(owner, loaded.owner());
		assertTrue(loaded.placed(0));
		assertTrue(loaded.paid(0));
		assertTrue(loaded.matches(42L));
		assertFalse(loaded.matches(43L));
	}

	@Test
	void oldSpeedsLoadAsTheDefaultDuration() {
		NbtCompound nbt = new NbtCompound();
		nbt.putInt("BlocksPerSecond", 4);
		nbt.putInt("Ticks", 11);
		BuildProgress loaded = new BuildProgress();
		loaded.read(nbt);
		assertEquals(BuildPace.DEFAULT, loaded.pace());
		assertEquals(DreamTableBlockEntity.PHASE_IDLE, loaded.phase());
	}
}
