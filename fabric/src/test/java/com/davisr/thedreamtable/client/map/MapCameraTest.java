package com.davisr.thedreamtable.client.map;

import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The free camera's motion, its ground and floor, its wall and the view directions. */
class MapCameraTest {
	private static final double DELTA = 1e-9;
	private static final Vec3d ORIGIN = Vec3d.ZERO;
	private static final Vec3d TABLE = new Vec3d(0.5, 64.5, 0.5);
	private static final Predicate<Vec3d> OPEN = point -> false;
	private static final double NO_FLOOR = -1000.0;

	private static void assertVec(Vec3d expected, Vec3d actual) {
		assertVec(expected, actual, DELTA);
	}

	private static void assertVec(Vec3d expected, Vec3d actual, double delta) {
		assertEquals(expected.x, actual.x, delta, "x");
		assertEquals(expected.y, actual.y, delta, "y");
		assertEquals(expected.z, actual.z, delta, "z");
	}

	@Test
	void yawZeroLooksSouthWithWestOnTheRight() {
		MapCamera camera = new MapCamera(ORIGIN, 0, 0);
		assertVec(new Vec3d(0, 0, 1), camera.forward());
		assertVec(new Vec3d(-1, 0, 0), camera.right());
	}

	@Test
	void yaw180LooksNorthWithEastOnTheRight() {
		MapCamera camera = new MapCamera(ORIGIN, 180, 0);
		assertVec(new Vec3d(0, 0, -1), camera.forward());
		assertVec(new Vec3d(1, 0, 0), camera.right());
	}

	@Test
	void forwardIsTheGamesOwnLookVector() {
		for (double[] angles : new double[][] {{0, 0}, {90, 30}, {-135, -60}, {200, 89}, {-40, -89}}) {
			MapCamera camera = new MapCamera(ORIGIN, angles[0], angles[1]);
			// Vec3d.fromPolar(pitch, yaw) is what Entity.getRotationVector computes, so the view matches the game's aim. Its sine
			// is MathHelper's lookup table, accurate to about 1e-4, hence the looser tolerance.
			assertVec(Vec3d.fromPolar((float) angles[1], (float) angles[0]), camera.forward(), 1e-3);
		}
	}

	@Test
	void rightStaysLevelWhateverThePitch() {
		MapCamera camera = new MapCamera(ORIGIN, 37, -25);
		assertEquals(1.0, camera.right().length(), DELTA);
		assertEquals(0.0, camera.right().y, DELTA);
		assertEquals(0.0, camera.forward().dotProduct(camera.right()), DELTA);
	}

	@Test
	void aPointOutsideTheWallIsPulledBackOntoItAndReported() {
		MapCamera camera = new MapCamera(new Vec3d(100.5, 64.5, 0.5), 0, 0);
		assertTrue(camera.clampTo(TABLE, 47));
		assertEquals(47, camera.position().distanceTo(TABLE), DELTA);
		assertVec(new Vec3d(47.5, 64.5, 0.5), camera.position());
	}

	@Test
	void aDiagonalOutsideTheWallIsPulledBackAlongItsDirection() {
		MapCamera camera = new MapCamera(TABLE.add(40, 40, 40), 0, 0);
		assertTrue(camera.clampTo(TABLE, 47));
		assertEquals(47, camera.position().distanceTo(TABLE), DELTA);
		assertVec(TABLE.add(47 / Math.sqrt(3), 47 / Math.sqrt(3), 47 / Math.sqrt(3)), camera.position());
	}

	@Test
	void aPointInsideTheWallIsLeftAloneAndNotReported() {
		Vec3d inside = new Vec3d(10.5, 70.5, -5.5);
		MapCamera camera = new MapCamera(inside, 0, 0);
		assertFalse(camera.clampTo(TABLE, 47));
		assertVec(inside, camera.position());
	}

	@Test
	void pitchStopsShortOfStraightUpAndDown() {
		MapCamera camera = new MapCamera(ORIGIN, 0, 0);
		camera.look(0, 500);
		assertEquals(89, camera.pitch(), DELTA);
		camera.look(0, -1000);
		assertEquals(-89, camera.pitch(), DELTA);
	}

	@Test
	void yawWrapsAroundWhileTurning() {
		MapCamera camera = new MapCamera(ORIGIN, 0, 0);
		camera.look(190, 0);
		assertEquals(-170, camera.yaw(), DELTA);
		assertVec(new MapCamera(ORIGIN, 190, 0).forward(), camera.forward());
	}

	@Test
	void forwardMovesTowardPlusZAtYawZero() {
		MapCamera camera = new MapCamera(ORIGIN, 0, 0);
		camera.move(1, 0, 0, 2, OPEN, NO_FLOOR);
		assertVec(new Vec3d(0, 0, 2), camera.position());
	}

	@Test
	void strafeRightAtYawZeroMovesWest() {
		MapCamera camera = new MapCamera(ORIGIN, 0, 0);
		camera.move(0, 1, 0, 1, OPEN, NO_FLOOR);
		assertVec(new Vec3d(-1, 0, 0), camera.position());
	}

	@Test
	void walkingFollowsTheYawNotThePitch() {
		MapCamera camera = new MapCamera(ORIGIN, 90, -60);
		camera.move(1, 0, 0, 1, OPEN, NO_FLOOR);
		assertVec(new Vec3d(-1, 0, 0), camera.position());
	}

	@Test
	void verticalMovesAlongWorldY() {
		MapCamera camera = new MapCamera(ORIGIN, 123, 45);
		camera.move(0, 0, 1, 3, OPEN, NO_FLOOR);
		assertVec(new Vec3d(0, 3, 0), camera.position());
		camera.move(0, 0, -1, 1, OPEN, NO_FLOOR);
		assertVec(new Vec3d(0, 2, 0), camera.position());
	}

	@Test
	void aDiagonalIsNoFasterThanAStraightMove() {
		MapCamera camera = new MapCamera(ORIGIN, 0, 0);
		camera.move(1, 1, 1, 1, OPEN, NO_FLOOR);
		assertEquals(1.0, camera.position().length(), DELTA);
	}

	@Test
	void noInputDoesNotMove() {
		MapCamera camera = new MapCamera(TABLE, 10, 10);
		camera.move(0, 0, 0, 5, OPEN, NO_FLOOR);
		assertVec(TABLE, camera.position());
	}

	@Test
	void dollyMovesAlongTheWholeLookDirection() {
		MapCamera camera = new MapCamera(ORIGIN, 0, 0);
		camera.dolly(3, OPEN, NO_FLOOR);
		assertVec(new Vec3d(0, 0, 3), camera.position());

		MapCamera down = new MapCamera(ORIGIN, 0, 60);
		down.dolly(2, OPEN, NO_FLOOR);
		assertVec(new Vec3d(0, -2 * Math.sin(Math.toRadians(60)), 2 * Math.cos(Math.toRadians(60))), down.position(), 1e-9);
	}

	@Test
	void aSolidCellStopsTheAxisItWouldEnter() {
		Predicate<Vec3d> wallAtZOne = point -> point.z >= 1.0;
		MapCamera camera = new MapCamera(ORIGIN, 0, 0);
		camera.move(1, 0, 0, 2, wallAtZOne, NO_FLOOR);
		// Steps of a quarter block: the step to z = 1 is solid, so the camera keeps the last free spot.
		assertEquals(0.75, camera.position().z, DELTA);
		assertEquals(0.0, camera.position().x, DELTA);
	}

	@Test
	void walkingIntoAWallSlidesAlongItOnTheOtherAxis() {
		Predicate<Vec3d> wallAtZOne = point -> point.z >= 1.0;
		MapCamera camera = new MapCamera(ORIGIN, 0, 0);
		// Forward and strafe left together: the forward axis hits the wall, the strafe keeps going.
		camera.move(1, -1, 0, 3, wallAtZOne, NO_FLOOR);
		assertTrue(camera.position().x > 2.0, "slides along the wall");
		assertTrue(camera.position().z > 0.9 && camera.position().z < 1.0, "stops at the wall: " + camera.position().z);
	}

	@Test
	void oneBlockWallStopsTheCameraHoweverFastItMoves() {
		Predicate<Vec3d> wall = point -> point.x >= 5.0 && point.x < 6.0;
		MapCamera camera = new MapCamera(ORIGIN, 0, 0);
		camera.move(0, -1, 0, 20, wall, NO_FLOOR);
		assertEquals(4.75, camera.position().x, DELTA);
	}

	@Test
	void theFloorStopsADescent() {
		MapCamera camera = new MapCamera(new Vec3d(0, 10, 0), 0, 0);
		camera.move(0, 0, -1, 5, OPEN, 8.0);
		assertEquals(8.0, camera.position().y, DELTA);
	}

	@Test
	void aSolidFloorPointCancelsTheDescentAtTheLastFreeSpot() {
		Predicate<Vec3d> groundTopAtEight = point -> point.y <= 8.0;
		MapCamera camera = new MapCamera(new Vec3d(0, 8.5, 0), 0, 0);
		camera.move(0, 0, -1, 1, groundTopAtEight, 8.0);
		assertEquals(8.25, camera.position().y, DELTA);
	}

	@Test
	void aDollyDownIntoGroundStopsAboveIt() {
		Predicate<Vec3d> groundBelowTen = point -> point.y <= 10.0;
		MapCamera camera = new MapCamera(new Vec3d(0, 20, 0), 0, 89);
		camera.dolly(30, groundBelowTen, NO_FLOOR);
		assertTrue(camera.position().y > 10.0 && camera.position().y < 10.1, "stops above the ground: " + camera.position().y);
	}

	@Test
	void moveToPutsTheCameraBack() {
		MapCamera camera = new MapCamera(ORIGIN, 0, 0);
		camera.move(1, 0, 0, 2, OPEN, NO_FLOOR);
		camera.moveTo(ORIGIN);
		assertVec(ORIGIN, camera.position());
	}
}
