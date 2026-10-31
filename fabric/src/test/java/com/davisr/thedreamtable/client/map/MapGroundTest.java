package com.davisr.thedreamtable.client.map;

import com.davisr.thedreamtable.client.map.MapGround.Cell;
import com.davisr.thedreamtable.client.map.MapGround.Probe;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The aim's column scan and the reach around the table. */
class MapGroundTest {
	private static final BlockPos TABLE = new BlockPos(0, 64, 0);
	private static final int RANGE = 48;

	/** A column whose only non-air block is {@code cell} at {@code y}. */
	private static Probe column(int y, Cell cell) {
		return (x, cellY, z) -> cellY == y ? cell : Cell.PASS;
	}

	@Test
	void solidGroundAnchorsOnTheCellAbove() {
		assertEquals(OptionalInt.of(11), MapGround.anchorY(column(10, Cell.SOLID), 0, 0, 0, 20));
	}

	@Test
	void replaceableGroundAnchorsOnItsOwnCell() {
		assertEquals(OptionalInt.of(10), MapGround.anchorY(column(10, Cell.REPLACEABLE), 0, 0, 0, 20));
	}

	@Test
	void aTableInTheColumnGivesNoAnchor() {
		Probe probe = (x, y, z) -> y == 12 ? Cell.TABLE : y == 10 ? Cell.SOLID : Cell.PASS;
		assertEquals(OptionalInt.empty(), MapGround.anchorY(probe, 0, 0, 0, 20));
	}

	@Test
	void anEmptyColumnGivesNoAnchor() {
		assertEquals(OptionalInt.empty(), MapGround.anchorY(column(-1, Cell.SOLID), 0, 0, 0, 20));
	}

	@Test
	void theScanStaysInsideItsWindow() {
		List<Integer> seen = new ArrayList<>();
		Probe probe = (x, y, z) -> {
			seen.add(y);
			return y == 3 ? Cell.SOLID : Cell.PASS;
		};
		assertEquals(OptionalInt.of(4), MapGround.anchorY(probe, 0, 0, 3, 10));
		assertEquals(10, Collections.max(seen));
		assertEquals(3, Collections.min(seen));
		assertEquals(OptionalInt.empty(), MapGround.anchorY(column(2, Cell.SOLID), 0, 0, 3, 10));
		assertEquals(OptionalInt.of(12), MapGround.anchorY(column(11, Cell.SOLID), 0, 0, 3, 11));
	}

	@Test
	void reachIncludesTheRangeItselfAndNothingBeyondIt() {
		assertTrue(MapGround.inRange(new BlockPos(48, 64, 0), TABLE, RANGE));
		assertFalse(MapGround.inRange(new BlockPos(49, 64, 0), TABLE, RANGE));
		assertTrue(MapGround.inRange(new BlockPos(33, 64, 33), TABLE, RANGE));
		assertFalse(MapGround.inRange(new BlockPos(34, 64, 34), TABLE, RANGE));
		assertTrue(MapGround.inRange(new BlockPos(0, 112, 0), TABLE, RANGE));
		assertFalse(MapGround.inRange(new BlockPos(0, 113, 0), TABLE, RANGE));
	}

	@Test
	void theTableCellIsNeverInReach() {
		assertFalse(MapGround.inRange(TABLE, TABLE, RANGE));
	}

	@Test
	void onFlatGroundTheEdgeIsTheRingOfLastColumnsInReach() {
		List<BlockPos> edge = MapGround.rangeEdge(TABLE, RANGE, (x, z) -> TABLE.getY());
		assertFalse(edge.isEmpty());
		for (BlockPos ground : edge) {
			assertEquals(TABLE.getY() - 1, ground.getY());
			double distance = Math.sqrt((double) ground.getX() * ground.getX() + (double) ground.getZ() * ground.getZ());
			assertTrue(distance <= RANGE && distance > RANGE - 2, "edge column at " + distance);
		}
		assertTrue(edge.contains(new BlockPos(RANGE, TABLE.getY() - 1, 0)));
		assertTrue(edge.contains(new BlockPos(0, TABLE.getY() - 1, -RANGE)));
	}

	@Test
	void theTableColumnDoesNotRingItself() {
		List<BlockPos> edge = MapGround.rangeEdge(TABLE, RANGE, (x, z) -> x == 0 && z == 0 ? Integer.MIN_VALUE : TABLE.getY());
		assertFalse(edge.contains(new BlockPos(1, TABLE.getY() - 1, 0)));
		assertFalse(edge.contains(new BlockPos(0, TABLE.getY() - 1, 1)));
	}

	@Test
	void aHoleWithoutGroundGetsAnEdgeAroundIt() {
		List<BlockPos> edge = MapGround.rangeEdge(TABLE, RANGE, (x, z) -> x == 10 && z == 10 ? Integer.MIN_VALUE : TABLE.getY());
		assertTrue(edge.contains(new BlockPos(9, TABLE.getY() - 1, 10)));
		assertTrue(edge.contains(new BlockPos(10, TABLE.getY() - 1, 11)));
	}

	@Test
	void higherGroundBringsTheEdgeCloserAsTheSphereBends() {
		int high = TABLE.getY() + 30;
		List<BlockPos> edge = MapGround.rangeEdge(TABLE, RANGE, (x, z) -> high);
		double reach = Math.sqrt((double) RANGE * RANGE - 30.0 * 30.0);
		for (BlockPos ground : edge) {
			double distance = Math.sqrt((double) ground.getX() * ground.getX() + (double) ground.getZ() * ground.getZ());
			assertTrue(distance <= reach && distance > reach - 2, "edge column at " + distance);
		}
	}
}
