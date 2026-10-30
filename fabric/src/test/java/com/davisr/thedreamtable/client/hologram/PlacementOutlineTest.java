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
	void edgesAreTwelveThinBoxesRunningAlongTheBoxEdges() {
		Box box = new Box(1, 2, 3, 6, 5, 5);
		double thickness = 0.25;
		List<Box> edges = PlacementOutline.edges(box, thickness);
		assertEquals(12, edges.size());
		Direction.Axis[] axes = Direction.Axis.values();
		for (int index = 0; index < edges.size(); index++) {
			Box edge = edges.get(index);
			Direction.Axis longAxis = axes[index / 4];
			for (Direction.Axis axis : axes) {
				String label = "edge " + index + " axis " + axis.name();
				if (axis == longAxis) {
					assertEquals(box.getMin(axis), edge.getMin(axis), DELTA, label);
					assertEquals(box.getMax(axis), edge.getMax(axis), DELTA, label);
				} else {
					assertEquals(thickness, edge.getMax(axis) - edge.getMin(axis), DELTA, label);
					double centre = (edge.getMin(axis) + edge.getMax(axis)) / 2.0;
					assertTrue(Math.abs(centre - box.getMin(axis)) < DELTA || Math.abs(centre - box.getMax(axis)) < DELTA, label);
				}
			}
		}
	}
}
