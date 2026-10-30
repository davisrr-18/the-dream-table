package com.davisr.thedreamtable.client.hologram;

import com.davisr.thedreamtable.client.hologram.GhostPlan.Kind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GhostPlanTest {
	private static final int CHUNK = GhostPlan.CHUNK;

	/** {@code parts} parts, one per step, {@code perLayer} parts on each layer from the bottom. */
	private static GhostPlan layered(int parts, int perLayer) {
		int[] steps = new int[parts];
		int[] layers = new int[parts];
		for (int index = 0; index < parts; index++) {
			steps[index] = index;
			layers[index] = index / perLayer;
		}
		return new GhostPlan(steps, layers);
	}

	@Test
	void splitsPartsIntoFixedChunks() {
		GhostPlan plan = layered(CHUNK * 2 + 5, 10);

		assertEquals(3, plan.chunks());
		assertEquals(0, plan.chunkStart(0));
		assertEquals(CHUNK, plan.chunkEnd(0));
		assertEquals(CHUNK * 2 + 5, plan.chunkEnd(2));
		assertEquals(0, GhostPlan.chunkCount(0));
		assertEquals(1, GhostPlan.chunkCount(1));
		assertEquals(1, GhostPlan.chunkCount(CHUNK));
		assertEquals(2, GhostPlan.chunkCount(CHUNK + 1));
	}

	@Test
	void findsTheFirstUnbuiltPartEvenWhenStepsHaveGaps() {
		// Steps 0, 1, 4, 5, 9: entities or invisible blocks leave no part.
		GhostPlan plan = new GhostPlan(new int[]{0, 1, 4, 5, 9}, new int[]{0, 0, 1, 1, 2});

		assertEquals(0, plan.firstUnbuilt(0));
		assertEquals(2, plan.firstUnbuilt(2));
		assertEquals(2, plan.firstUnbuilt(4));
		assertEquals(3, plan.firstUnbuilt(5));
		assertEquals(4, plan.firstUnbuilt(6));
		assertEquals(5, plan.firstUnbuilt(10), "everything built");
	}

	@Test
	void chunksBeforeTheNextBlockAreBuiltAndItsChunkIsLive() {
		GhostPlan plan = layered(CHUNK * 4, 1000);
		int first = plan.firstUnbuilt(CHUNK * 2 + 7);

		assertEquals(Kind.BUILT, plan.kind(0, first, Integer.MAX_VALUE));
		assertEquals(Kind.BUILT, plan.kind(1, first, Integer.MAX_VALUE));
		assertEquals(Kind.LIVE, plan.kind(2, first, Integer.MAX_VALUE));
		assertEquals(Kind.BAKED, plan.kind(3, first, Integer.MAX_VALUE));
	}

	@Test
	void aChunkStartingExactlyAtTheNextBlockIsLiveSoTheNextOneCanGlow() {
		GhostPlan plan = layered(CHUNK * 3, 1000);
		int first = plan.firstUnbuilt(CHUNK);

		assertEquals(Kind.BUILT, plan.kind(0, first, Integer.MAX_VALUE));
		assertEquals(Kind.LIVE, plan.kind(1, first, Integer.MAX_VALUE));
		assertEquals(Kind.BAKED, plan.kind(2, first, Integer.MAX_VALUE));
	}

	@Test
	void chunksAboveTheVisibleLayersAreHiddenNotBaked() {
		// 4 parts per layer: chunk 0 covers layers 0-31, chunk 1 layers 32-63, chunk 2 layers 64-95.
		GhostPlan plan = layered(CHUNK * 3, 4);

		assertEquals(Kind.LIVE, plan.kind(0, 0, 10));
		assertEquals(Kind.HIDDEN, plan.kind(1, 0, 10));
		assertEquals(Kind.HIDDEN, plan.kind(2, 0, 10));
		assertEquals(Kind.BAKED, plan.kind(1, 0, 32), "a chunk that reaches into the window is baked");
		assertEquals(Kind.HIDDEN, plan.kind(2, 0, 63));
		assertEquals(Kind.BAKED, plan.kind(2, 0, 64));
	}

	@Test
	void everyWindowAboveTheTopSharesOneBakedSet() {
		GhostPlan plan = layered(CHUNK, 4);

		assertEquals(plan.maxLayer(), plan.window(Integer.MAX_VALUE));
		assertEquals(plan.maxLayer(), plan.window(plan.maxLayer() + 50));
		assertEquals(3, plan.window(3));
	}

	@Test
	void nothingIsLeftToDrawWhenEverythingIsBuilt() {
		GhostPlan plan = layered(10, 5);

		assertEquals(plan.parts(), plan.firstUnbuilt(1000));
		assertEquals(Kind.BUILT, plan.kind(0, plan.parts(), Integer.MAX_VALUE));
	}

	@Test
	void anEmptyFarmHasNoChunks() {
		GhostPlan plan = new GhostPlan(new int[0], new int[0]);

		assertEquals(0, plan.chunks());
		assertEquals(0, plan.firstUnbuilt(0));
	}

	@Test
	void rejectsMismatchedArrays() {
		assertThrows(IllegalArgumentException.class, () -> new GhostPlan(new int[2], new int[1]));
	}
}
