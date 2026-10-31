package com.davisr.thedreamtable.client.map;

import com.davisr.thedreamtable.client.map.MapGround.Cell;
import com.davisr.thedreamtable.client.map.MapGround.Ground;
import com.davisr.thedreamtable.client.map.MapGround.Probe;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The aim's column scan, the reach around the table, and the ground picked under a point of the map. */
class MapGroundTest {
	private static final BlockPos TABLE = new BlockPos(0, 64, 0);
	private static final int RANGE = 48;
	private static final double DELTA = 1e-9;

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
	void anchorAtCombinesTheScanWithTheReach() {
		Probe ground = column(63, Cell.SOLID);
		assertEquals(Optional.of(new BlockPos(10, 64, 10)), MapGround.anchorAt(ground, TABLE, 10, 10, RANGE));
		assertEquals(Optional.empty(), MapGround.anchorAt(ground, TABLE, 40, 40, RANGE));
		assertEquals(Optional.empty(), MapGround.anchorAt(column(15, Cell.SOLID), TABLE, 1, 0, RANGE));
		assertEquals(Optional.of(new BlockPos(1, 17, 0)), MapGround.anchorAt(column(16, Cell.SOLID), TABLE, 1, 0, RANGE));
		assertEquals(Optional.empty(), MapGround.anchorAt(column(64, Cell.TABLE), TABLE, 1, 0, RANGE));
	}

	@Test
	void aCameraStraightAboveTheCentreLooksAtTheCamera() {
		Optional<Ground> ground = MapGround.pick(5, 100, -7, 0, 0, 0.5, 2, (x, z) -> 64);
		assertEquals(5, ground.orElseThrow().x(), DELTA);
		assertEquals(-7, ground.orElseThrow().z(), DELTA);
		assertEquals(64, ground.orElseThrow().y(), DELTA);
	}

	@Test
	void theRightSideOfTheScreenIsEastAndTheTopIsNorth() {
		Ground east = MapGround.pick(0, 100, 0, 1, 0, 0.5, 2, (x, z) -> 64).orElseThrow();
		assertEquals(36, east.x(), DELTA);
		assertEquals(0, east.z(), DELTA);
		Ground north = MapGround.pick(0, 100, 0, 0, 1, 0.5, 2, (x, z) -> 64).orElseThrow();
		assertEquals(0, north.x(), DELTA);
		assertEquals(-18, north.z(), DELTA);
	}

	@Test
	void aRaisedGroundMovesThePickTowardTheCamera() {
		Ground plain = MapGround.pick(0, 100, 0, 1, 0, 0.5, 2, (x, z) -> 64).orElseThrow();
		Ground raised = MapGround.pick(0, 100, 0, 1, 0, 0.5, 2, (x, z) -> x > 10 ? 80 : 64).orElseThrow();
		assertEquals(36, plain.x(), DELTA);
		assertEquals(20, raised.x(), DELTA);
		assertEquals(80, raised.y(), DELTA);
	}

	@Test
	void theReturnedPointProjectsBackOntoTheScreenPoint() {
		double camX = 3;
		double camY = 100;
		double camZ = -4;
		double ndcX = 0.6;
		double ndcY = -0.4;
		double tanHalfFovY = 0.5;
		double aspect = 2;
		Ground ground = MapGround.pick(camX, camY, camZ, ndcX, ndcY, tanHalfFovY, aspect, (x, z) -> 64 + 0.1 * x).orElseThrow();
		double depth = camY - ground.y();
		assertEquals(ndcX, (ground.x() - camX) / (tanHalfFovY * aspect * depth), 1.0E-3);
		assertEquals(ndcY, (camZ - ground.z()) / (tanHalfFovY * depth), 1.0E-3);
		assertEquals(64 + 0.1 * ground.x(), ground.y(), DELTA);
	}

	@Test
	void noGroundUnderThePickGivesNothing() {
		assertEquals(Optional.empty(), MapGround.pick(0, 100, 0, 0, 0, 0.5, 2, (x, z) -> Double.NaN));
		assertEquals(Optional.empty(), MapGround.pick(0, 100, 0, 1, 0, 0.5, 2, (x, z) -> x > 10 ? Double.NaN : 64));
	}

	@Test
	void aGroundAboveTheCameraGivesNothing() {
		assertEquals(Optional.empty(), MapGround.pick(0, 100, 0, 0, 0, 0.5, 2, (x, z) -> 120));
	}
}
