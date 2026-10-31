package com.davisr.thedreamtable.client.map;

import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The free camera's motion, its wall and the view directions the aim is cast along. */
class MapCameraTest {
	private static final double DELTA = 1e-9;
	private static final Vec3d ORIGIN = Vec3d.ZERO;
	private static final Vec3d TABLE = new Vec3d(0.5, 64.5, 0.5);

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
	void centreOfTheScreenIsTheLookDirection() {
		for (double[] angles : new double[][] {{0, 0}, {90, 30}, {-135, -60}, {200, 89}}) {
			MapCamera camera = new MapCamera(ORIGIN, angles[0], angles[1]);
			assertVec(camera.forward(), camera.rayThrough(0, 0, 0.5, 1.6));
		}
	}

	@Test
	void pitchDownLooksDownAndTheTopOfTheScreenFacesForward() {
		MapCamera camera = new MapCamera(ORIGIN, 180, 60);
		assertTrue(camera.forward().y < 0);
		assertTrue(camera.up().y > 0);
		assertVec(new Vec3d(0, 0, -1), new MapCamera(ORIGIN, 180, 89).up(), 0.02);
	}

	@Test
	void rightAndUpAreUnitAndPerpendicularToTheLookDirection() {
		MapCamera camera = new MapCamera(ORIGIN, 37, -25);
		assertEquals(1.0, camera.forward().length(), DELTA);
		assertEquals(1.0, camera.right().length(), DELTA);
		assertEquals(1.0, camera.up().length(), DELTA);
		assertEquals(0.0, camera.forward().dotProduct(camera.right()), DELTA);
		assertEquals(0.0, camera.forward().dotProduct(camera.up()), DELTA);
		assertEquals(0.0, camera.right().dotProduct(camera.up()), DELTA);
		assertEquals(0.0, camera.right().y, DELTA);
	}

	@Test
	void ndcRightIsTheCameraRight() {
		MapCamera camera = new MapCamera(ORIGIN, 35, 20);
		Vec3d ray = camera.rayThrough(1, 0, 0.5, 2);
		assertTrue(ray.dotProduct(camera.right()) > 0);
		assertEquals(0.0, ray.dotProduct(camera.up()), DELTA);
	}

	@Test
	void ndcUpIsTheCameraUp() {
		MapCamera camera = new MapCamera(ORIGIN, 35, 20);
		Vec3d ray = camera.rayThrough(0, 1, 0.5, 2);
		assertTrue(ray.dotProduct(camera.up()) > 0);
		assertEquals(0.0, ray.dotProduct(camera.right()), DELTA);
	}

	@Test
	void theEdgeOfTheScreenIsHalfTheFieldAwayFromTheCentre() {
		// Half the vertical FOV's tangent is 0.5 and the aspect is 2, so the right edge is 1 across at unit depth: 45 degrees.
		Vec3d ray = new MapCamera(ORIGIN, 0, 0).rayThrough(1, 0, 0.5, 2);
		assertVec(new Vec3d(-Math.sqrt(0.5), 0, Math.sqrt(0.5)), ray);
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
		assertTrue(Double.isFinite(camera.up().y));
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
		camera.move(1, 0, 0, 2);
		assertVec(new Vec3d(0, 0, 2), camera.position());
	}

	@Test
	void strafeRightAtYawZeroMovesWest() {
		MapCamera camera = new MapCamera(ORIGIN, 0, 0);
		camera.move(0, 1, 0, 1);
		assertVec(new Vec3d(-1, 0, 0), camera.position());
	}

	@Test
	void walkingFollowsTheYawNotThePitch() {
		MapCamera camera = new MapCamera(ORIGIN, 90, -60);
		camera.move(1, 0, 0, 1);
		assertVec(new Vec3d(-1, 0, 0), camera.position());
	}

	@Test
	void verticalMovesAlongWorldY() {
		MapCamera camera = new MapCamera(ORIGIN, 123, 45);
		camera.move(0, 0, 1, 3);
		assertVec(new Vec3d(0, 3, 0), camera.position());
		camera.move(0, 0, -1, 1);
		assertVec(new Vec3d(0, 2, 0), camera.position());
	}

	@Test
	void aDiagonalIsNoFasterThanAStraightMove() {
		MapCamera camera = new MapCamera(ORIGIN, 0, 0);
		camera.move(1, 1, 1, 1);
		assertEquals(1.0, camera.position().length(), DELTA);
	}

	@Test
	void noInputDoesNotMove() {
		MapCamera camera = new MapCamera(TABLE, 10, 10);
		camera.move(0, 0, 0, 5);
		assertVec(TABLE, camera.position());
	}

	@Test
	void dollyMovesAlongTheWholeLookDirection() {
		MapCamera camera = new MapCamera(ORIGIN, 0, 0);
		camera.dolly(3);
		assertVec(new Vec3d(0, 0, 3), camera.position());

		MapCamera down = new MapCamera(ORIGIN, 0, 60);
		down.dolly(2);
		assertVec(new Vec3d(0, -2 * Math.sin(Math.toRadians(60)), 2 * Math.cos(Math.toRadians(60))), down.position(), 1e-9);
	}
}
