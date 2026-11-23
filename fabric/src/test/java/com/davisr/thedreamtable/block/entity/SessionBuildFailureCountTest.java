package com.davisr.thedreamtable.block.entity;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SessionBuildFailureCountTest {
	private static final BlockPos SPOT = new BlockPos(10, 64, -3);

	/** A table reloaded at step 5 whose saved issue is {@code saved}, as a table saves and loads it. */
	private static BuildProgress reloadedAtStepFive(BuildIssue saved) {
		BuildProgress progress = new BuildProgress();
		progress.start(UUID.randomUUID(), true, 7L);
		for (int step = 0; step < 5; step++) {
			progress.advance();
		}
		progress.setIssue(saved);
		progress.stop(DreamTableBlockEntity.PHASE_BLOCKED);
		NbtCompound nbt = new NbtCompound();
		progress.write(nbt);
		BuildProgress loaded = new BuildProgress();
		loaded.read(nbt);
		assertEquals(5, loaded.index());
		return loaded;
	}

	private static BuildIssue placeFailed(BlockPos where, int attempts) {
		return new BuildIssue(BuildIssue.Kind.PLACE_FAILED, where, "minecraft:stone", attempts);
	}

	@Test
	void aFailureAfterAReloadContinuesTheSavedCountAtTheSameStep() {
		BuildProgress loaded = reloadedAtStepFive(placeFailed(SPOT, 1));

		assertEquals(2, new SessionBuild(null, loaded, null).countFailure(BuildIssue.Kind.PLACE_FAILED, SPOT));
	}

	@Test
	void aSaveFromBeforeTheStepWasKeptCountsItsIssueAtTheSavedStep() {
		NbtCompound nbt = new NbtCompound();
		nbt.putString("Phase", DreamTableBlockEntity.PHASE_BLOCKED);
		nbt.putInt("BuildIndex", 5);
		placeFailed(SPOT, 1).write(nbt);
		BuildProgress loaded = new BuildProgress();
		loaded.read(nbt);

		assertEquals(2, new SessionBuild(null, loaded, null).countFailure(BuildIssue.Kind.PLACE_FAILED, SPOT));
	}

	@Test
	void aFailureOfAnotherKindAtTheSameSpotStartsOver() {
		BuildProgress loaded = reloadedAtStepFive(placeFailed(SPOT, 1));

		assertEquals(1, new SessionBuild(null, loaded, null).countFailure(BuildIssue.Kind.ERROR, SPOT));
	}

	@Test
	void aFailureAtAnotherSpotStartsOver() {
		BuildProgress loaded = reloadedAtStepFive(placeFailed(SPOT, 1));

		assertEquals(1, new SessionBuild(null, loaded, null).countFailure(BuildIssue.Kind.PLACE_FAILED, new BlockPos(0, 64, 0)));
	}

	@Test
	void aSavedIssueFromAnEarlierStepDoesNotCountForANewStep() {
		BuildProgress loaded = reloadedAtStepFive(placeFailed(SPOT, 1));
		loaded.advance();

		assertEquals(1, new SessionBuild(null, loaded, null).countFailure(BuildIssue.Kind.PLACE_FAILED, SPOT));
	}

	@Test
	void repeatedFailuresWithinOneSessionStillEscalate() {
		BuildProgress progress = new BuildProgress();
		progress.start(UUID.randomUUID(), true, 7L);
		SessionBuild build = new SessionBuild(null, progress, null);

		assertEquals(1, build.countFailure(BuildIssue.Kind.PLACE_FAILED, SPOT));
		assertEquals(2, build.countFailure(BuildIssue.Kind.PLACE_FAILED, SPOT));
	}

	/** The real flow: "Try again" calls progress.start, which clears the issue, before the next failure is counted. */
	@Test
	void aFailureAfterATryAgainContinuesTheSavedCount() {
		BuildProgress loaded = reloadedAtStepFive(placeFailed(SPOT, 2));
		BuildIssue resumed = loaded.issueAtCurrentStep();
		loaded.start(UUID.randomUUID(), false, 7L);
		assertEquals(null, loaded.issueAtCurrentStep(), "start clears the issue, so the seed must be carried");
		SessionBuild build = new SessionBuild(null, loaded, null);
		build.carryIssue(resumed);

		assertEquals(3, build.countFailure(BuildIssue.Kind.PLACE_FAILED, SPOT));
	}

	@Test
	void aCarriedIssueOfAnotherSpotDoesNotCount() {
		BuildProgress loaded = reloadedAtStepFive(placeFailed(SPOT, 2));
		BuildIssue resumed = loaded.issueAtCurrentStep();
		loaded.start(UUID.randomUUID(), false, 7L);
		SessionBuild build = new SessionBuild(null, loaded, null);
		build.carryIssue(resumed);

		assertEquals(1, build.countFailure(BuildIssue.Kind.PLACE_FAILED, SPOT.up()));
	}
}
