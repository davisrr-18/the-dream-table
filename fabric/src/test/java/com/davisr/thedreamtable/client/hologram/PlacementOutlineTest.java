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
	void barsGrowWithDistanceAndMeetInSolidCorners() {
		Box box = new Box(0, 0, 0, 10, 4, 6);
		Vec3d near = new Vec3d(-1, 0, 0);
		Vec3d far = new Vec3d(-80, 0, 0);
		List<Box> close = PlacementOutline.bars(box, near, 0.1, 0.006, 4.0);
		List<Box> distant = PlacementOutline.bars(box, far, 0.1, 0.006, 4.0);
		assertTrue(close.size() >= 12);
		for (Box bar : close) {
			double thin = Math.min(Math.min(bar.getLengthX(), bar.getLengthY()), bar.getLengthZ());
			assertTrue(thin >= 0.1 - DELTA && thin <= PlacementOutline.MAX_THICKNESS + DELTA);
		}
		double closeWidth = close.stream().mapToDouble(bar -> Math.min(Math.min(bar.getLengthX(), bar.getLengthY()), bar.getLengthZ())).max().orElse(0);
		double distantWidth = distant.stream().mapToDouble(bar -> Math.min(Math.min(bar.getLengthX(), bar.getLengthY()), bar.getLengthZ())).min().orElse(0);
		assertTrue(distantWidth > closeWidth);
		for (double x : new double[] {box.minX, box.maxX}) {
			for (double y : new double[] {box.minY, box.maxY}) {
				for (double z : new double[] {box.minZ, box.maxZ}) {
					Vec3d corner = new Vec3d(x, y, z);
					assertTrue(close.stream().filter(bar -> bar.contains(corner)).count() >= 3, "corner " + corner);
				}
			}
		}
	}

	@Test
	void barsCoverTheWholeLengthOfAnEdgeWithoutGaps() {
		Box box = new Box(0, 0, 0, 20, 2, 2);
		List<Box> bars = PlacementOutline.bars(box, new Vec3d(10, 50, 10), 0.1, 0.006, 4.0);
		for (double x = 0; x <= 20; x += 0.25) {
			Vec3d point = new Vec3d(x, 0, 0);
			assertTrue(bars.stream().anyMatch(bar -> bar.contains(point)), "x " + x);
		}
	}
}
