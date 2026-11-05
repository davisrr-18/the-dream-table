package com.davisr.thedreamtable.block.entity;

import com.davisr.thedreamtable.GameSetup;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Infinite water near the table, decided on a made-up world of water cells. */
class NearbyWaterTest {
	private static final BlockPos TABLE = new BlockPos(100, 64, 100);
	private static final FluidState SOURCE = Fluids.WATER.getStill(false);
	private static final FluidState FLOWING = Fluids.WATER.getFlowing(3, false);

	@BeforeAll
	static void setUp() {
		GameSetup.bootstrap();
	}

	/** Water cells by position; cells listed as unloaded or farm-owned are never read as sources. */
	private static final class Pond implements NearbyWater.Cells {
		private final Map<BlockPos, FluidState> fluids = new HashMap<>();
		private final Set<BlockPos> unloaded = new HashSet<>();
		private final Set<BlockPos> owned = new HashSet<>();

		Pond sources(BlockPos... cells) {
			for (BlockPos cell : cells) {
				fluids.put(cell, SOURCE);
			}
			return this;
		}

		Pond flowing(BlockPos cell) {
			fluids.put(cell, FLOWING);
			return this;
		}

		@Override
		public boolean loaded(BlockPos pos) {
			return !unloaded.contains(pos);
		}

		@Override
		public FluidState fluid(BlockPos pos) {
			return fluids.getOrDefault(pos, Fluids.EMPTY.getDefaultState());
		}

		@Override
		public boolean farmOwned(BlockPos pos) {
			return owned.contains(pos);
		}

		boolean nearby() {
			return NearbyWater.nearby(this, TABLE);
		}
	}

	private static final BlockPos CENTER = TABLE.add(1, 0, 0);

	@Test
	void aSingleSourceDoesNotCount() {
		assertFalse(new Pond().sources(CENTER).nearby());
	}

	@Test
	void twoSourcesSideBySideDoNotCount() {
		// Each one has a single source neighbour, which is not enough to stay a source forever.
		assertFalse(new Pond().sources(CENTER, CENTER.east()).nearby());
	}

	@Test
	void aSourceWithTwoSourceNeighboursCounts() {
		assertTrue(new Pond().sources(CENTER, CENTER.east(), CENTER.south()).nearby());
	}

	@Test
	void verticalNeighboursDoNotCount() {
		assertFalse(new Pond().sources(CENTER, CENTER.up(), CENTER.down()).nearby());
	}

	@Test
	void flowingWaterIsNotASource() {
		assertFalse(new Pond().sources(CENTER, CENTER.east()).flowing(CENTER.south()).nearby());
		assertFalse(new Pond().sources(CENTER).flowing(CENTER.east()).flowing(CENTER.south()).nearby());
	}

	@Test
	void sixBlocksFromTheTableIsInReach() {
		BlockPos edge = TABLE.add(6, 0, 0);
		assertTrue(new Pond().sources(edge, edge.west(), edge.south()).nearby());
	}

	@Test
	void sevenBlocksFromTheTableIsOutOfReach() {
		BlockPos beyond = TABLE.add(7, 0, 0);
		assertFalse(new Pond().sources(beyond, beyond.east(), beyond.south()).nearby());
	}

	@Test
	void unloadedCellsAreIgnored() {
		Pond unloadedNeighbour = new Pond().sources(CENTER, CENTER.east(), CENTER.south());
		unloadedNeighbour.unloaded.add(CENTER.south());
		assertFalse(unloadedNeighbour.nearby());

		Pond unloadedCenter = new Pond().sources(CENTER, CENTER.east(), CENTER.south());
		unloadedCenter.unloaded.add(CENTER);
		assertFalse(unloadedCenter.nearby());
	}

	@Test
	void waterInsideAFarmDoesNotCount() {
		Pond ownedNeighbour = new Pond().sources(CENTER, CENTER.east(), CENTER.south());
		ownedNeighbour.owned.add(CENTER.east());
		assertFalse(ownedNeighbour.nearby());

		Pond ownedCenter = new Pond().sources(CENTER, CENTER.east(), CENTER.south());
		ownedCenter.owned.add(CENTER);
		assertFalse(ownedCenter.nearby());
	}
}
