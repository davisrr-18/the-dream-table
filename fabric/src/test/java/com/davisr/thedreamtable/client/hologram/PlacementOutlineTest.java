package com.davisr.thedreamtable.client.hologram;

import com.davisr.thedreamtable.GameSetup;
import com.davisr.thedreamtable.client.hologram.PlacementOutline.Status;
import com.davisr.thedreamtable.farm.FarmStep;
import com.davisr.thedreamtable.farm.FarmTemplate;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The placement outline's colour mapping, the footprint it shows for a spot, and the edge boxes drawn for a box. */
class PlacementOutlineTest {
	private static final BlockPos TABLE = new BlockPos(0, 64, 0);
	private static final BlockPos ANCHOR = new BlockPos(20, 64, 20);
	private static final double DELTA = 1e-9;

	@BeforeAll
	static void bootstrap() {
		GameSetup.bootstrap();
	}

	private static FarmTemplate uneven() {
		return FarmTemplate.of("uneven", List.of(
				FarmStep.block(0, 0, 0, Blocks.STONE.getDefaultState(), Items.STONE),
				FarmStep.block(4, 0, 0, Blocks.STONE.getDefaultState(), Items.STONE),
				FarmStep.block(4, 2, 1, Blocks.STONE.getDefaultState(), Items.STONE)));
	}

	@Test
	void statusFollowsWhetherTheServerAnsweredAndWhatItSaid() {
		assertEquals(Status.CHECKING, PlacementOutline.status(false, ""));
		assertEquals(Status.CHECKING, PlacementOutline.status(false, "Blocked by a wall"));
		assertEquals(Status.VALID, PlacementOutline.status(true, ""));
		assertEquals(Status.VALID, PlacementOutline.status(true, null));
		assertEquals(Status.REFUSED, PlacementOutline.status(true, "Blocked by a wall"));
	}

	@Test
	void footprintTurnsWithTheFacingAndKeepsTheHeight() {
		FarmTemplate farm = uneven();
		for (boolean mirrored : new boolean[] {false, true}) {
			Box south = PlacementOutline.footprint(farm, TABLE, ANCHOR, Direction.SOUTH, mirrored);
			for (Direction facing : Direction.Type.HORIZONTAL) {
				Box box = PlacementOutline.footprint(farm, TABLE, ANCHOR, facing, mirrored);
				String label = facing.asString() + " mirrored=" + mirrored;
				boolean quarterTurn = facing.getAxis() == Direction.Axis.X;
				double expectedX = quarterTurn ? south.maxZ - south.minZ : south.maxX - south.minX;
				double expectedZ = quarterTurn ? south.maxX - south.minX : south.maxZ - south.minZ;
				assertEquals(expectedX, box.maxX - box.minX, DELTA, "x extent " + label);
				assertEquals(expectedZ, box.maxZ - box.minZ, DELTA, "z extent " + label);
				assertEquals(3.0, box.maxY - box.minY, DELTA, "height " + label);
				assertTrue(box.minX <= ANCHOR.getX() && ANCHOR.getX() + 1 <= box.maxX, "anchor x " + label);
				assertTrue(box.minZ <= ANCHOR.getZ() && ANCHOR.getZ() + 1 <= box.maxZ, "anchor z " + label);
				assertTrue(box.minY <= ANCHOR.getY() && ANCHOR.getY() < box.maxY, "anchor y " + label);
			}
		}
	}

	@Test
	void mirroringKeepsTheFootprintInPlace() {
		FarmTemplate farm = uneven();
		for (Direction facing : Direction.Type.HORIZONTAL) {
			assertEquals(
					PlacementOutline.footprint(farm, TABLE, ANCHOR, facing, false),
					PlacementOutline.footprint(farm, TABLE, ANCHOR, facing, true),
					facing.asString());
		}
	}

	@Test
	void ribbonsKeepTheirWidthOnScreenAndAreCutBehindTheCamera() {
		Box box = new Box(0, 0, 0, 10, 4, 6);
		Vec3d camera = new Vec3d(-5, 2, 3);
		Vec3d forward = new Vec3d(1, 0, 0);
		List<Vec3d[]> ribbons = PlacementOutline.ribbons(box, camera, forward, 0.1, 0.0026);
		assertEquals(12, ribbons.size());
		for (Vec3d[] ribbon : ribbons) {
			assertEquals(4, ribbon.length);
			for (Vec3d corner : ribbon) {
				assertTrue(corner.subtract(camera).dotProduct(forward) > 0.1 - 0.05);
			}
			double widthA = ribbon[0].distanceTo(ribbon[1]);
			double distanceA = ribbon[0].add(ribbon[1]).multiply(0.5).distanceTo(camera);
			assertEquals(0.0052, widthA / distanceA, 0.0005);
		}
	}

	@Test
	void aLineEntirelyBehindTheCameraDrawsNothing() {
		Box box = new Box(0, 0, 0, 2, 2, 2);
		assertTrue(PlacementOutline.ribbons(box, new Vec3d(10, 1, 1), new Vec3d(1, 0, 0), 0.1, 0.0026).isEmpty());
	}

	@Test
	void theAimedBlockGetsEightFlatBracketArmsHuggingItsEdgesFromInside() {
		BlockPos anchor = new BlockPos(4, 70, -3);
		List<Vec3d[]> arms = PlacementOutline.brackets(anchor, new Vec3d(4.5, 75, 0), new Vec3d(0, -1, 0), 0.1, 0.0104, 0.05);
		assertEquals(8, arms.size());
		for (Vec3d[] arm : arms) {
			for (Vec3d corner : arm) {
				assertEquals(70.03, corner.y, 0.001);
				assertTrue(corner.x >= 4 - 1.0E-6 && corner.x <= 5 + 1.0E-6 && corner.z >= -3 - 1.0E-6 && corner.z <= -2 + 1.0E-6, "inside the cell " + corner);
			}
		}
		// Each corner of the block is covered by two arms.
		for (double x : new double[] {4, 5}) {
			for (double z : new double[] {-3, -2}) {
				Vec3d corner = new Vec3d(x, 70.03, z);
				long covering = arms.stream().filter(arm -> inside(arm, corner)).count();
				assertTrue(covering >= 2, "corner " + corner);
			}
		}
	}

	private static boolean inside(Vec3d[] ribbon, Vec3d point) {
		double minX = Double.MAX_VALUE;
		double maxX = -Double.MAX_VALUE;
		double minZ = Double.MAX_VALUE;
		double maxZ = -Double.MAX_VALUE;
		for (Vec3d corner : ribbon) {
			minX = Math.min(minX, corner.x);
			maxX = Math.max(maxX, corner.x);
			minZ = Math.min(minZ, corner.z);
			maxZ = Math.max(maxZ, corner.z);
		}
		return point.x >= minX && point.x <= maxX && point.z >= minZ && point.z <= maxZ;
	}

	@Test
	void bracketsNeverWidenPastTheirCap() {
		BlockPos anchor = new BlockPos(0, 64, 0);
		List<Vec3d[]> arms = PlacementOutline.brackets(anchor, new Vec3d(0.5, 70, -300), new Vec3d(0, 0, 1), 0.1, 0.0104, 0.05);
		for (Vec3d[] arm : arms) {
			assertTrue(Math.min(arm[0].distanceTo(arm[1]), arm[1].distanceTo(arm[2])) <= 0.1 + 1.0E-6);
		}
	}

	@Test
	void theBeamRisesFromTheBlockToTheGivenHeight() {
		BlockPos anchor = new BlockPos(2, 64, 2);
		List<Vec3d[]> beam = PlacementOutline.beam(anchor, 4.0, new Vec3d(10, 66, 2.5), new Vec3d(-1, 0, 0), 0.1, 0.0078, 0.08);
		assertEquals(1, beam.size());
		Vec3d[] ribbon = beam.get(0);
		assertTrue(ribbon[0].y < ribbon[2].y);
		assertEquals(ribbon[0].y, ribbon[1].y, 1.0E-6);
		assertEquals(ribbon[2].y, ribbon[3].y, 1.0E-6);
		assertTrue(ribbon[2].y >= 68.0);
	}

	/** Horizontal distance of a loop point from the centre of the table's cell. */
	private static double reach(Vec3d point) {
		return Math.hypot(point.x - 0.5, point.z - 0.5);
	}
}
