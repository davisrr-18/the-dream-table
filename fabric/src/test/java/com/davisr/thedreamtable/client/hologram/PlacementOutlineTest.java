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
}
