package com.davisr.thedreamtable.farm.schematic;

import com.davisr.thedreamtable.GameSetup;
import com.davisr.thedreamtable.farm.FarmStep;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.nbt.NbtList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.davisr.thedreamtable.farm.schematic.LitematicaTestFiles.pack;
import static com.davisr.thedreamtable.farm.schematic.LitematicaTestFiles.region;
import static com.davisr.thedreamtable.farm.schematic.LitematicaTestFiles.tile;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Where tile entities and entities land when a Litematica file holds several regions. */
class LitematicaRegionsTest {
	private static final String[] PALETTE = {"minecraft:air", "minecraft:stone", "minecraft:chest"};

	@BeforeAll
	static void setUp() {
		GameSetup.bootstrap();
	}

	private static List<FarmStep> read(NbtCompound... regions) {
		NbtCompound all = new NbtCompound();
		for (int index = 0; index < regions.length; index++) {
			all.put("region" + index, regions[index]);
		}
		NbtCompound root = new NbtCompound();
		root.put("Regions", all);
		SchematicReader.Result result = SchematicReader.fromRoot("nbt:regions", root, true);
		assertNotNull(result.template(), () -> result.notes().toString());
		return result.template().steps();
	}

	private static FarmStep at(List<FarmStep> steps, int x, int y, int z) {
		return steps.stream().filter(step -> !step.isEntity() && step.x() == x && step.y() == y && step.z() == z).findFirst().orElseThrow();
	}

	private static String name(List<FarmStep> steps, int x, int y, int z) {
		FarmStep step = at(steps, x, y, z);
		return step.blockNbt() == null ? null : step.blockNbt().getString("CustomName");
	}

	private static NbtCompound xyz(int x, int y, int z) {
		NbtCompound position = new NbtCompound();
		position.putInt("x", x);
		position.putInt("y", y);
		position.putInt("z", z);
		return position;
	}

	@Test
	void positionAndSizeAreReadFromLitematicasXyzCompounds() {
		NbtCompound region = region(new int[0], new int[0], pack(2, 1, 2, 1), PALETTE);
		region.put("Position", xyz(4, 0, 0));
		region.put("Size", xyz(3, 1, 1));
		List<FarmStep> steps = read(region);
		assertEquals(3, steps.size());
		assertEquals(Blocks.CHEST, at(steps, 1, 0, 0).state().getBlock());
	}

	@Test
	void aTileReachesItsOwnRegionsBlockNotAnotherRegionsBlockAtTheSameNumbers() {
		NbtCompound first = region(new int[]{0, 0, 0}, new int[]{3, 1, 1}, pack(2, 1, 2, 1), PALETTE);
		first.put("TileEntities", list(tile(1, 0, 0, "first")));
		// Its chest is at x=6; the tile says x=1, which is where the first region's chest stands.
		NbtCompound second = region(new int[]{5, 0, 0}, new int[]{3, 1, 1}, pack(2, 1, 2, 1), PALETTE);
		second.put("TileEntities", list(tile(1, 0, 0, "second")));

		List<FarmStep> steps = read(first, second);

		assertEquals("first", name(steps, 1, 0, 0));
		assertEquals("second", name(steps, 6, 0, 0));
		assertNull(at(steps, 0, 0, 0).blockNbt());
		assertNull(at(steps, 5, 0, 0).blockNbt());
	}

	@Test
	void regionsAtOtherPositionsAndWithNegativeSizesKeepTheirTilesOnTheirBlocks() {
		// Lowest corner (-3, 2, 4): stone at x=-3, chest at x=-2.
		NbtCompound alpha = region(new int[]{-3, 2, 4}, new int[]{2, 1, 1}, pack(2, 1, 2), PALETTE);
		alpha.put("TileEntities", list(tile(1, 0, 0, "alpha")));
		// Grows toward smaller x from 20: its lowest corner is x=19, so the cells are x=19 (stone) and x=20 (chest).
		NbtCompound gamma = region(new int[]{20, 0, 0}, new int[]{-2, 1, 1}, pack(2, 1, 2), PALETTE);
		gamma.put("TileEntities", list(tile(1, 0, 0, "gamma")));
		// Two chests along z.
		NbtCompound beta = region(new int[]{10, 5, 0}, new int[]{1, 1, 2}, pack(2, 2, 2), PALETTE);
		beta.put("TileEntities", list(tile(0, 0, 0, "beta0"), tile(0, 0, 1, "beta1")));

		List<FarmStep> steps = read(alpha, gamma, beta);

		// The lowest corner of everything is (-3, 0, 0), so every step moves by +3 on x and 0 on y and z.
		assertEquals(Blocks.STONE.getDefaultState(), at(steps, 0, 2, 4).state());
		assertEquals("alpha", name(steps, 1, 2, 4));
		assertEquals(Blocks.STONE.getDefaultState(), at(steps, 22, 0, 0).state());
		assertEquals("gamma", name(steps, 23, 0, 0));
		assertEquals("beta0", name(steps, 13, 5, 0));
		assertEquals("beta1", name(steps, 13, 5, 1));
		assertNull(name(steps, 0, 2, 4));
		assertNull(name(steps, 22, 0, 0));
	}

	@Test
	void aTileWrittenInSchematicCoordinatesStillFindsItsBlock() {
		NbtCompound only = region(new int[]{4, 0, 0}, new int[]{2, 1, 1}, pack(2, 1, 2), PALETTE);
		// Outside the region's own 0..1 range, so it is read as a position in the schematic: step x=1.
		only.put("TileEntities", list(tile(5, 0, 0, "schematic")));

		List<FarmStep> steps = read(only);

		assertEquals("schematic", name(steps, 1, 0, 0));
		assertNull(name(steps, 0, 0, 0));
	}

	@Test
	void entitiesAreMovedByTheirRegionsPosition() {
		NbtCompound first = region(new int[]{0, 0, 0}, new int[]{1, 1, 1}, pack(2, 1), PALETTE);
		NbtCompound second = region(new int[]{5, 3, 7}, new int[]{1, 1, 1}, pack(2, 1), PALETTE);
		NbtCompound cart = new NbtCompound();
		cart.putString("id", "minecraft:minecart");
		NbtList position = new NbtList();
		position.add(NbtDouble.of(1.5));
		position.add(NbtDouble.of(0.0));
		position.add(NbtDouble.of(0.5));
		cart.put("Pos", position);
		second.put("Entities", list(cart));

		List<FarmStep> steps = read(first, second);

		FarmStep entity = steps.stream().filter(FarmStep::isEntity).findFirst().orElseThrow();
		assertEquals(EntityType.MINECART, entity.entity());
		assertTrue(entity.x() == 6 && entity.y() == 3 && entity.z() == 7, () -> entity.x() + "," + entity.y() + "," + entity.z());
	}

	private static NbtList list(NbtCompound... entries) {
		NbtList list = new NbtList();
		for (NbtCompound entry : entries) {
			list.add(entry);
		}
		return list;
	}
}
