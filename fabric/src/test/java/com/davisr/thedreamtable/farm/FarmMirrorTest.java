package com.davisr.thedreamtable.farm;

import com.davisr.thedreamtable.GameSetup;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.enums.ChestType;
import net.minecraft.block.enums.DoorHinge;
import net.minecraft.block.enums.StairShape;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.EmptyBlockView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Mirroring a farm: where its blocks land, how their states turn, and that nothing saved changes for farms that are not mirrored. */
class FarmMirrorTest {
	private static final BlockPos TABLE = new BlockPos(0, 64, 0);
	private static final BlockPos ANCHOR = new BlockPos(20, 64, 20);

	@BeforeAll
	static void bootstrap() {
		GameSetup.bootstrap();
	}

	private static FarmTemplate lopsided() {
		return FarmTemplate.of("lopsided", List.of(
				FarmStep.block(0, 0, 0, Blocks.STONE.getDefaultState(), Items.STONE),
				FarmStep.block(4, 0, 0, Blocks.STONE.getDefaultState(), Items.STONE),
				FarmStep.block(4, 2, 2, Blocks.STONE.getDefaultState(), Items.STONE)));
	}

	@Test
	void mirroredPositionsAreTheFlippedOffsetsThenTurned() {
		FarmTemplate farm = lopsided();
		BlockPos center = farm.center();
		assertEquals(4, farm.mirrorSum());
		for (Direction facing : Direction.Type.HORIZONTAL) {
			FarmSite plain = new FarmSite(TABLE, ANCHOR, facing, false);
			FarmSite mirrored = new FarmSite(TABLE, ANCHOR, facing, true);
			for (FarmStep step : farm.steps()) {
				BlockPos offset = new BlockPos(step.x() - center.getX(), step.y() - center.getY(), step.z() - center.getZ());
				// An odd width flips about the center column itself; the even-width case is covered separately.
				BlockPos flipped = new BlockPos(-offset.getX(), offset.getY(), offset.getZ());
				assertEquals(ANCHOR.add(offset.rotate(FarmGeometry.rotation(facing))), plain.target(farm, step), facing.asString());
				assertEquals(ANCHOR.add(flipped.rotate(FarmGeometry.rotation(facing))), mirrored.target(farm, step), facing.asString());
			}
		}
	}

	@Test
	void mirroringTwiceRestoresTheFarm() {
		FarmTemplate farm = lopsided();
		for (Direction facing : Direction.Type.HORIZONTAL) {
			for (FarmStep step : farm.steps()) {
				// The offset from the anchor flips along the farm's own x axis, whatever the turn.
				BlockPos plain = new FarmSite(TABLE, ANCHOR, facing, false).target(farm, step).subtract(ANCHOR);
				BlockPos mirrored = new FarmSite(TABLE, ANCHOR, facing, true).target(farm, step).subtract(ANCHOR);
				assertEquals(plain.getY(), mirrored.getY());
				if (facing.getAxis() == Direction.Axis.Z) {
					assertEquals(-plain.getX(), mirrored.getX(), facing.asString());
					assertEquals(plain.getZ(), mirrored.getZ(), facing.asString());
				} else {
					assertEquals(plain.getX(), mirrored.getX(), facing.asString());
					assertEquals(-plain.getZ(), mirrored.getZ(), facing.asString());
				}
			}
		}
	}

	@Test
	void mirroredFootprintKeepsItsSizeAndStaysAroundTheAnchor() {
		FarmTemplate farm = lopsided();
		for (Direction facing : Direction.Type.HORIZONTAL) {
			Box plain = new FarmSite(TABLE, ANCHOR, facing, false).footprint(farm);
			Box mirrored = new FarmSite(TABLE, ANCHOR, facing, true).footprint(farm);
			assertEquals(plain.getLengthX(), mirrored.getLengthX(), 1.0E-6, facing.asString());
			assertEquals(plain.getLengthY(), mirrored.getLengthY(), 1.0E-6, facing.asString());
			assertEquals(plain.getLengthZ(), mirrored.getLengthZ(), 1.0E-6, facing.asString());
			assertEquals(plain.minY, mirrored.minY, 1.0E-6, facing.asString());
			assertTrue(mirrored.contains(ANCHOR.getX() + 0.5, ANCHOR.getY() + 0.5, ANCHOR.getZ() + 0.5), facing.asString());
		}
	}

	/** A solid block of the given size: its mirror image is itself, so mirroring must not move a single cell. */
	private static FarmTemplate solid(int width, int depth) {
		List<FarmStep> steps = new java.util.ArrayList<>();
		for (int x = 0; x < width; x++) {
			for (int z = 0; z < depth; z++) {
				steps.add(FarmStep.block(x, 0, z, Blocks.STONE.getDefaultState(), Items.STONE));
			}
		}
		return FarmTemplate.of("solid_" + width + "x" + depth, steps);
	}

	private static java.util.Set<BlockPos> cells(FarmTemplate farm, FarmSite site) {
		java.util.Set<BlockPos> cells = new java.util.HashSet<>();
		for (FarmStep step : farm.steps()) {
			cells.add(site.target(farm, step));
		}
		return cells;
	}

	@Test
	void mirroredFootprintCoversTheSameCellsForEvenAndOddWidths() {
		for (int width = 1; width <= 8; width++) {
			FarmTemplate farm = solid(width, 3);
			for (Direction facing : Direction.Type.HORIZONTAL) {
				assertEquals(cells(farm, new FarmSite(TABLE, ANCHOR, facing, false)), cells(farm, new FarmSite(TABLE, ANCHOR, facing, true)),
						"width " + width + " facing " + facing.asString());
			}
		}
	}

	@Test
	void mirroredLopsidedFarmOfEvenWidthStaysInsideTheSameBox() {
		// Width 4 (x 0..3) with the blocks at both ends and a taller one at the low-x end.
		FarmTemplate farm = FarmTemplate.of("even_lopsided", List.of(
				FarmStep.block(0, 0, 0, Blocks.STONE.getDefaultState(), Items.STONE),
				FarmStep.block(3, 0, 1, Blocks.STONE.getDefaultState(), Items.STONE),
				FarmStep.block(0, 3, 1, Blocks.STONE.getDefaultState(), Items.STONE)));
		assertEquals(3, farm.mirrorSum());
		for (Direction facing : Direction.Type.HORIZONTAL) {
			assertEquals(new FarmSite(TABLE, ANCHOR, facing, false).footprint(farm), new FarmSite(TABLE, ANCHOR, facing, true).footprint(farm), facing.asString());
		}
		// The block at the low-x end lands at the high-x end of the same box.
		FarmSite plain = new FarmSite(TABLE, ANCHOR, Direction.SOUTH, false);
		FarmSite mirrored = new FarmSite(TABLE, ANCHOR, Direction.SOUTH, true);
		FarmStep low = farm.steps().get(0);
		assertEquals(plain.target(farm, low).getX() + 3, mirrored.target(farm, low).getX());
	}

	@Test
	void everyBuiltInFarmMirrorsInsideItsOwnBox() {
		for (FarmOptions.FarmOption option : FarmOptions.ALL) {
			FarmTemplate farm = FarmTemplates.get(option.id());
			for (Direction facing : Direction.Type.HORIZONTAL) {
				assertEquals(new FarmSite(TABLE, ANCHOR, facing, false).footprint(farm), new FarmSite(TABLE, ANCHOR, facing, true).footprint(farm),
						option.id() + " " + facing.asString());
			}
		}
	}

	@Test
	void mirrorSumIsDerivedFromTheStepsSoOlderConstructionsKeepWorking() {
		FarmTemplate farm = solid(4, 2);
		FarmTemplate copy = new FarmTemplate("copy", farm.steps(), farm.materials(), farm.center());
		assertEquals(farm.mirrorSum(), copy.mirrorSum());
		assertEquals(3, copy.mirrorSum());
		assertEquals(0, FarmTemplate.of("empty", List.of()).mirrorSum());
	}

	@Test
	void notMirroredIsTheDefaultAndChangesNothing() {
		FarmTemplate farm = lopsided();
		for (Direction facing : Direction.Type.HORIZONTAL) {
			FarmSite legacy = new FarmSite(TABLE, ANCHOR, facing);
			assertFalse(legacy.mirrored());
			assertEquals(new FarmSite(TABLE, ANCHOR, facing, false), legacy);
			BlockState stairs = Blocks.OAK_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.EAST);
			assertEquals(stairs.rotate(FarmGeometry.rotation(facing)), legacy.orient(stairs), facing.asString());
		}
		FarmStep far = farm.steps().get(2);
		assertNotEquals(new FarmSite(TABLE, ANCHOR, Direction.NORTH, true).target(farm, far), new FarmSite(TABLE, ANCHOR, Direction.NORTH, false).target(farm, far));
	}

	@Test
	void tableRelativeSitesMirrorToo() {
		FarmStep step = FarmStep.block(3, 1, 2, Blocks.STONE.getDefaultState(), Items.STONE);
		BlockPos plain = FarmGeometry.toWorld(TABLE, Direction.SOUTH, false, step);
		BlockPos mirrored = FarmGeometry.toWorld(TABLE, Direction.SOUTH, true, step);
		assertEquals(TABLE.offset(Direction.SOUTH, FarmGeometry.FORWARD_GAP).add(3, 1, 2), plain);
		assertEquals(TABLE.offset(Direction.SOUTH, FarmGeometry.FORWARD_GAP).add(-3, 1, 2), mirrored);
	}

	// States

	@Test
	void facingBlocksSwapSidesWhenMirrored() {
		BlockState hopper = Blocks.HOPPER.getDefaultState().with(Properties.HOPPER_FACING, Direction.EAST);
		assertEquals(Direction.WEST, FarmGeometry.orient(hopper, Direction.SOUTH, true).get(Properties.HOPPER_FACING));
		assertEquals(Direction.EAST, FarmGeometry.orient(hopper, Direction.SOUTH, false).get(Properties.HOPPER_FACING));
		// Turned afterwards: east flipped to west, then a quarter turn (facing WEST of the table) takes it to north.
		assertEquals(Direction.NORTH, FarmGeometry.orient(hopper, Direction.WEST, true).get(Properties.HOPPER_FACING));

		BlockState observer = Blocks.OBSERVER.getDefaultState().with(Properties.FACING, Direction.WEST);
		assertEquals(Direction.EAST, FarmGeometry.orient(observer, Direction.SOUTH, true).get(Properties.FACING));
		BlockState piston = Blocks.STICKY_PISTON.getDefaultState().with(Properties.FACING, Direction.UP);
		assertEquals(Direction.UP, FarmGeometry.orient(piston, Direction.NORTH, true).get(Properties.FACING));
		BlockState rail = Blocks.RAIL.getDefaultState().with(Properties.RAIL_SHAPE, net.minecraft.block.enums.RailShape.ASCENDING_EAST);
		assertEquals(net.minecraft.block.enums.RailShape.ASCENDING_WEST, FarmGeometry.orient(rail, Direction.SOUTH, true).get(Properties.RAIL_SHAPE));
	}

	@Test
	void stairsFlipTheirFacingAndCorner() {
		BlockState stairs = Blocks.OAK_STAIRS.getDefaultState().with(StairsBlock.FACING, Direction.EAST).with(StairsBlock.SHAPE, StairShape.INNER_LEFT);
		BlockState mirrored = FarmGeometry.orient(stairs, Direction.SOUTH, true);
		assertEquals(Direction.WEST, mirrored.get(StairsBlock.FACING));
		assertEquals(StairShape.INNER_RIGHT, mirrored.get(StairsBlock.SHAPE));
		assertEquals(StairShape.INNER_LEFT, FarmGeometry.orient(stairs, Direction.SOUTH, false).get(StairsBlock.SHAPE));
	}

	/**
	 * The strongest check on a block's mirroring: its outline after {@code orient} must be the unmirrored outline flipped
	 * along x, for every state. This is what found vanilla leaving stairs corners and double chests on the wrong side.
	 */
	@Test
	void mirroredBlocksLookLikeTheirFlippedOriginals() {
		Block[] blocks = {Blocks.OAK_STAIRS, Blocks.OAK_DOOR, Blocks.OAK_TRAPDOOR, Blocks.OAK_FENCE_GATE, Blocks.CHEST, Blocks.TRAPPED_CHEST,
				Blocks.HOPPER, Blocks.LEVER, Blocks.STONE_BUTTON, Blocks.RAIL, Blocks.POWERED_RAIL, Blocks.PISTON, Blocks.PISTON_HEAD,
				Blocks.RED_BED, Blocks.GRINDSTONE, Blocks.LECTERN, Blocks.ANVIL, Blocks.END_ROD, Blocks.LIGHTNING_ROD, Blocks.REPEATER,
				Blocks.COMPARATOR, Blocks.OAK_WALL_SIGN, Blocks.LADDER, Blocks.OAK_SLAB};
		for (Block block : blocks) {
			for (BlockState state : block.getStateManager().getStates()) {
				assertArrayEquals(sample(state, true), sample(FarmGeometry.orient(state, Direction.SOUTH, true), false), state.toString());
			}
		}
	}

	/** 16 samples per axis at cell centers: never on a face of a shape, so containment is unambiguous. */
	private static boolean[] sample(BlockState state, boolean flipX) {
		VoxelShape shape = state.getOutlineShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN);
		boolean[] cells = new boolean[16 * 16 * 16];
		for (int i = 0; i < 16; i++) {
			for (int j = 0; j < 16; j++) {
				for (int k = 0; k < 16; k++) {
					boolean inside = false;
					for (Box box : shape.getBoundingBoxes()) {
						inside |= box.contains((i + 0.5) / 16, (j + 0.5) / 16, (k + 0.5) / 16);
					}
					cells[(flipX ? 15 - i : i) * 256 + j * 16 + k] = inside;
				}
			}
		}
		return cells;
	}

	@Test
	void doorsSwapHingeWhenMirrored() {
		BlockState door = Blocks.OAK_DOOR.getDefaultState().with(DoorBlock.FACING, Direction.EAST).with(DoorBlock.HINGE, DoorHinge.LEFT);
		BlockState mirrored = FarmGeometry.orient(door, Direction.SOUTH, true);
		assertEquals(Direction.WEST, mirrored.get(DoorBlock.FACING));
		assertEquals(DoorHinge.RIGHT, mirrored.get(DoorBlock.HINGE));
		assertEquals(DoorHinge.LEFT, FarmGeometry.orient(door, Direction.SOUTH, false).get(DoorBlock.HINGE));
	}

	@Test
	void chestHalvesSwapSoTheyStayPaired() {
		// A double chest as a template stores it: two chests side by side along x, facing south.
		for (Direction facing : Direction.Type.HORIZONTAL) {
			for (boolean mirrored : new boolean[] {false, true}) {
				for (ChestType firstType : new ChestType[] {ChestType.LEFT, ChestType.RIGHT}) {
					BlockState first = Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.SOUTH).with(ChestBlock.CHEST_TYPE, firstType);
					BlockState second = first.with(ChestBlock.CHEST_TYPE, firstType.getOpposite());
					Direction towardSecond = ChestBlock.getFacing(first);
					FarmTemplate farm = FarmTemplate.of("double_chest", List.of(
							FarmStep.block(0, 0, 0, first, Items.CHEST),
							FarmStep.block(towardSecond.getOffsetX(), 0, towardSecond.getOffsetZ(), second, Items.CHEST)));
					assertChestsPaired(farm, new FarmSite(TABLE, ANCHOR, facing, mirrored), facing.asString() + " mirrored=" + mirrored + " first=" + firstType);
				}
			}
		}
		BlockState single = Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.EAST);
		assertEquals(ChestType.SINGLE, FarmGeometry.orient(single, Direction.SOUTH, true).get(ChestBlock.CHEST_TYPE));
	}

	@Test
	void everyBuiltInFarmKeepsItsChestsPairedInEveryOrientation() {
		int farms = 0;
		for (FarmOptions.FarmOption option : FarmOptions.ALL) {
			FarmTemplate farm = FarmTemplates.get(option.id());
			assertNotNull(farm, option.id());
			farms++;
			for (Direction facing : Direction.Type.HORIZONTAL) {
				for (boolean mirrored : new boolean[] {false, true}) {
					assertChestsPaired(farm, new FarmSite(TABLE, ANCHOR, facing, mirrored), option.id() + " " + facing.asString() + " mirrored=" + mirrored);
				}
			}
		}
		assertTrue(farms > 0);
	}

	/** Every double chest half must have its partner exactly where its type and facing point, with the opposite type. */
	private static void assertChestsPaired(FarmTemplate farm, FarmSite site, String message) {
		Map<BlockPos, BlockState> world = new HashMap<>();
		for (FarmStep step : farm.steps()) {
			if (!step.isEntity() && step.state() != null) {
				world.put(site.target(farm, step), site.orient(step.state()));
			}
		}
		for (Map.Entry<BlockPos, BlockState> entry : world.entrySet()) {
			BlockState state = entry.getValue();
			if (!(state.getBlock() instanceof ChestBlock) || state.get(ChestBlock.CHEST_TYPE) == ChestType.SINGLE) {
				continue;
			}
			BlockState partner = world.get(entry.getKey().offset(ChestBlock.getFacing(state)));
			assertNotNull(partner, message + ": the partner of the chest at " + entry.getKey() + " is missing");
			assertEquals(state.getBlock(), partner.getBlock(), message);
			assertEquals(state.get(ChestBlock.FACING), partner.get(ChestBlock.FACING), message);
			assertEquals(state.get(ChestBlock.CHEST_TYPE).getOpposite(), partner.get(ChestBlock.CHEST_TYPE), message);
		}
	}

	// Saving

	@Test
	void siteNbtRoundTripsTheFlag() {
		for (boolean mirrored : new boolean[] {false, true}) {
			FarmSite site = new FarmSite(TABLE, ANCHOR, Direction.WEST, mirrored);
			NbtCompound nbt = new NbtCompound();
			site.write(nbt);
			assertEquals(site, FarmSite.read(nbt));
			assertEquals(mirrored, FarmSite.read(nbt).mirrored());
		}
	}

	@Test
	void siteSavedWithoutTheTagIsNotMirrored() {
		NbtCompound nbt = new NbtCompound();
		new FarmSite(TABLE, ANCHOR, Direction.EAST).write(nbt);
		assertFalse(nbt.contains("Mirrored"), "a farm that is not mirrored writes exactly what 1.0.0 wrote");
		assertFalse(FarmSite.read(nbt).mirrored());

		NbtCompound legacy = new NbtCompound();
		legacy.putLong("Table", TABLE.asLong());
		legacy.putLong("Anchor", ANCHOR.asLong());
		legacy.putString("Facing", "north");
		assertEquals(new FarmSite(TABLE, ANCHOR, Direction.NORTH, false), FarmSite.read(legacy));
	}

	@Test
	void finishedFarmKeepsItsMirrorThroughTheWorldSave() {
		FarmTemplate farm = FarmTemplates.get(FarmOptions.SUGAR_CANE.id());
		FarmAreas areas = new FarmAreas();
		java.util.UUID player = java.util.UUID.fromString("10000000-0000-0000-0000-000000000001");
		FarmSite site = new FarmSite(TABLE, ANCHOR, Direction.EAST, true);
		areas.recordFinished(player, farm, site, Map.of());

		FarmAreas restored = FarmAreas.read(areas.writeNbt(new NbtCompound(), null), null);

		FarmAreas.Finished finished = restored.finishedBy(player).getFirst();
		assertTrue(finished.site().mirrored());
		assertEquals(site, finished.site());
		assertEquals(areas.finishedBy(player).getFirst().box(), finished.box());
	}
}
