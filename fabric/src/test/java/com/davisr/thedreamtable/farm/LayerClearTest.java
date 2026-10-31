package com.davisr.thedreamtable.farm;

import com.davisr.thedreamtable.GameSetup;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The cursor, budget and layer of a footprint clear: each cell of a layer once, a bounded number per tick, and the step cells known. */
class LayerClearTest {
	private static final BlockPos TABLE = new BlockPos(0, 64, 0);
	private static final BlockPos ANCHOR = new BlockPos(20, 64, 20);

	@BeforeAll
	static void bootstrap() {
		GameSetup.bootstrap();
	}

	/** A footprint starting at x 10, y 64, z -5, four layers high, with the given size and step cells. */
	private static LayerClear footprint(int width, int depth, BlockPos... steps) {
		LongOpenHashSet cells = new LongOpenHashSet();
		for (BlockPos step : steps) {
			cells.add(step.asLong());
		}
		return new LayerClear(10, 64, -5, width, 4, depth, cells);
	}

	@Test
	void aLayerIsWalkedOnceCellByCellInTheFootprint() {
		LayerClear clear = footprint(3, 2);
		clear.start(1);
		Set<BlockPos> seen = new HashSet<>();
		for (BlockPos cell = clear.current(); cell != null; cell = clear.current()) {
			assertTrue(seen.add(cell), "visited twice: " + cell);
			clear.advance();
		}
		assertEquals(6, seen.size());
		assertTrue(seen.contains(new BlockPos(10, 65, -5)));
		assertTrue(seen.contains(new BlockPos(12, 65, -4)));
		assertTrue(seen.stream().allMatch(cell -> cell.getY() == 65));
	}

	@Test
	void layersAreCountedFromTheLowestBlockOfTheFootprint() {
		LayerClear clear = footprint(3, 2);
		assertEquals(0, clear.layerOf(new BlockPos(10, 64, -5)));
		assertEquals(3, clear.layerOf(new BlockPos(0, 67, 0)));
	}

	@Test
	void stepCellsAreKnownByPosition() {
		LayerClear clear = footprint(3, 2, new BlockPos(11, 65, -4));
		assertTrue(clear.isStepCell(new BlockPos(11, 65, -4)));
		assertFalse(clear.isStepCell(new BlockPos(10, 65, -4)));
		assertFalse(clear.isStepCell(new BlockPos(11, 66, -4)));
	}

	@Test
	void eachTickLooksAtNoMoreThanTheBudgetAndTheBudgetRenewsNextTick() {
		LayerClear clear = footprint(20, 20);
		clear.start(0);
		int looked = 0;
		while (clear.hasBudget(7L)) {
			clear.advance();
			looked++;
		}
		assertEquals(LayerClear.CELLS_PER_TICK, looked);
		assertFalse(clear.hasBudget(7L));
		assertTrue(clear.hasBudget(8L));
		assertNotNull(clear.current());
	}

	@Test
	void aLayerKeepsItsCursorAcrossTicksAndAnotherLayerStartsOver() {
		LayerClear clear = footprint(20, 20);
		clear.start(0);
		for (int i = 0; i < 5; i++) {
			clear.advance();
		}
		BlockPos next = clear.current();
		clear.start(0);
		assertEquals(next, clear.current());
		clear.start(1);
		assertEquals(new BlockPos(10, 65, -5), clear.current());
	}

	@Test
	void finishingALayerForgetsItsCursor() {
		LayerClear clear = footprint(2, 2);
		clear.start(0);
		clear.advance();
		clear.finish();
		clear.start(0);
		assertEquals(new BlockPos(10, 64, -5), clear.current());
	}

	@Test
	void aLayerIsClearedOnceAndNeverDuringARepair() {
		BitSet cleared = new BitSet();
		assertEquals(0, LayerClearer.layerDue(false, cleared::get, 2));
		cleared.set(0);
		assertEquals(1, LayerClearer.layerDue(false, cleared::get, 2));
		assertEquals(-1, LayerClearer.layerDue(true, cleared::get, 2));
		cleared.set(1, 3);
		assertEquals(-1, LayerClearer.layerDue(false, cleared::get, 2));
	}

	@Test
	void layersWithoutAStepOfTheirOwnAreClearedOnTheWayUp() {
		BitSet cleared = new BitSet();
		cleared.set(0);
		cleared.set(2);
		// Layer 1 holds no step (or only growth steps): reaching layer 3 still clears it first, from the bottom.
		assertEquals(1, LayerClear.nextUncleared(cleared::get, 3));
		assertEquals(-1, LayerClear.nextUncleared(cleared::get, 0));
	}

	@Test
	void theFootprintKnowsItsTopLayerAndItsBox() {
		LayerClear clear = footprint(3, 2);
		assertEquals(3, clear.topLayer());
		assertTrue(clear.contains(new BlockPos(12, 67, -4)));
		assertFalse(clear.contains(new BlockPos(12, 68, -4)));
		assertFalse(clear.contains(new BlockPos(13, 64, -5)));
		assertFalse(clear.contains(new BlockPos(10, 63, -5)));
		assertFalse(clear.contains(new BlockPos(10, 64, -3)));
	}

	@Test
	void aBuiltInStyleFarmHasEveryStepInItsFootprintAtItsOwnLayer() {
		FarmTemplate farm = FarmTemplate.of("tower", List.of(
				FarmStep.block(0, 0, 0, Blocks.STONE.getDefaultState(), Items.STONE),
				FarmStep.block(2, 3, 1, Blocks.STONE.getDefaultState(), Items.STONE)));
		for (Direction facing : Direction.Type.HORIZONTAL) {
			for (boolean mirrored : new boolean[]{false, true}) {
				FarmSite site = new FarmSite(TABLE, ANCHOR, facing, mirrored);
				LayerClear clear = LayerClear.of(farm, site);
				BlockPos low = site.target(farm, farm.steps().get(0));
				BlockPos high = site.target(farm, farm.steps().get(1));
				assertTrue(clear.isStepCell(low), facing.asString());
				assertTrue(clear.isStepCell(high), facing.asString());
				assertEquals(0, clear.layerOf(low), facing.asString());
				assertEquals(3, clear.layerOf(high), facing.asString());
				assertFalse(clear.isStepCell(low.up()), facing.asString());
			}
		}
	}

	@Test
	void theClearWalksEveryCellOfAStepsLayerWithinTheFootprint() {
		FarmTemplate farm = FarmTemplate.of("tower", List.of(
				FarmStep.block(0, 0, 0, Blocks.STONE.getDefaultState(), Items.STONE),
				FarmStep.block(2, 0, 1, Blocks.STONE.getDefaultState(), Items.STONE)));
		FarmSite site = new FarmSite(TABLE, ANCHOR, Direction.SOUTH, false);
		LayerClear clear = LayerClear.of(farm, site);
		BlockPos step = site.target(farm, farm.steps().get(0));
		clear.start(clear.layerOf(step));
		Set<BlockPos> cells = new HashSet<>();
		for (BlockPos cell = clear.current(); cell != null; cell = clear.current()) {
			cells.add(cell);
			clear.advance();
		}
		assertEquals(6, cells.size());
		assertTrue(cells.contains(step));
		assertTrue(cells.contains(site.target(farm, farm.steps().get(1))));
		assertNull(clear.current());
	}
}
