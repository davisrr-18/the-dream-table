package com.davisr.thedreamtable.farm;

import com.davisr.thedreamtable.GameSetup;
import com.davisr.thedreamtable.farm.schematic.SchematicReader;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reads every bundled farm schematic and checks what the build relies on. */
class BuiltInFarmsTest {
	private static final String[] FILES = {
			"sugar_cane_farm", "sugar_cane_farm_medium", "sugar_cane_farm_big",
			"bamboo_farm", "bamboo_farm_medium", "bamboo_farm_big",
			"crop_farm_tiny", "crop_farm", "mob_farm", "mob_farm_big", "iron_farm"};

	@BeforeAll
	static void setUp() {
		GameSetup.bootstrap();
	}

	private static FarmTemplate read(String file) throws IOException {
		try (InputStream stream = BuiltInFarmsTest.class.getResourceAsStream("/data/the_dream_table/structures/" + file + ".schem")) {
			assertNotNull(stream, file + " is missing");
			return SchematicReader.read(file, stream);
		}
	}

	@Test
	void everyBundledFarmLoads() throws IOException {
		for (String file : FILES) {
			FarmTemplate farm = read(file);
			assertNotNull(farm, file);
			assertFalse(farm.steps().isEmpty(), file);
			assertTrue(farm.steps().size() <= SchematicReader.MAX_BLOCKS, file);
			assertTrue(farm.materials().size() <= FarmTemplate.MAX_SLOTS, file);
		}
	}

	@Test
	void materialsAddUpToTheSteps() throws IOException {
		for (String file : FILES) {
			FarmTemplate farm = read(file);
			long withItem = farm.steps().stream().filter(step -> step.item() != null).count();
			int listed = farm.materials().stream().mapToInt(MaterialSpec::count).sum();
			assertEquals(withItem, listed, file);
		}
	}

	@Test
	void sizesLandInTheirBand() throws IOException {
		String[][] expected = {
				{"sugar_cane_farm", "TINY"}, {"sugar_cane_farm_medium", "MEDIUM"}, {"sugar_cane_farm_big", "BIG"},
				{"bamboo_farm", "TINY"}, {"bamboo_farm_medium", "MEDIUM"}, {"bamboo_farm_big", "BIG"},
				{"crop_farm_tiny", "TINY"}, {"crop_farm", "MEDIUM"},
				{"mob_farm", "MEDIUM"}, {"mob_farm_big", "BIG"}};
		for (String[] farm : expected) {
			assertEquals(FarmSize.valueOf(farm[1]), FarmSize.of(read(farm[0]).steps().size()), farm[0]);
		}
	}

	@Test
	void everyFarmIsInExactlyOneGroupAndEveryGroupInOneCategory() {
		for (FarmOptions.FarmOption option : FarmOptions.ALL) {
			long groups = java.util.Arrays.stream(FarmOptions.Group.values()).filter(group -> group.sizes().contains(option)).count();
			assertEquals(1, groups, option.id());
		}
		for (FarmOptions.Group group : FarmOptions.Group.values()) {
			long categories = java.util.Arrays.stream(FarmOptions.Category.values()).filter(category -> category.groups().contains(group)).count();
			assertEquals(1, categories, group.name());
		}
		int listed = java.util.Arrays.stream(FarmOptions.Group.values()).mapToInt(group -> group.sizes().size()).sum();
		assertEquals(FarmOptions.ALL.size(), listed);
	}

	@Test
	void sizesOfAGroupGrowFromSmallestToBiggest() {
		for (FarmOptions.Group group : FarmOptions.Group.values()) {
			int previous = -1;
			for (FarmOptions.FarmOption option : group.sizes()) {
				FarmTemplate template = FarmTemplates.get(option.id());
				assertNotNull(template, option.id());
				int size = FarmSize.of(template.steps().size()).ordinal();
				assertTrue(size > previous, option.id());
				previous = size;
			}
		}
	}

	@Test
	void signatureIsStableAcrossReads() throws IOException {
		for (String file : FILES) {
			assertEquals(read(file).signature(), read(file).signature(), file);
		}
	}

	@Test
	void turningTheFarmKeepsItsSizeAndAnchor() throws IOException {
		FarmTemplate farm = read("iron_farm");
		BlockPos table = new BlockPos(0, 64, 0);
		BlockPos anchor = new BlockPos(10, 64, 10);
		Box north = new FarmSite(table, anchor, Direction.NORTH).footprint(farm);
		for (Direction facing : Direction.Type.HORIZONTAL) {
			FarmSite site = new FarmSite(table, anchor, facing);
			Box box = site.footprint(farm);
			assertEquals(north.getLengthY(), box.getLengthY(), 1.0E-6, facing.asString());
			assertEquals(north.getLengthX() * north.getLengthZ(), box.getLengthX() * box.getLengthZ(), 1.0E-6, facing.asString());
			assertEquals(anchor.getY(), (int) box.minY, facing.asString());
			assertTrue(box.contains(anchor.getX() + 0.5, anchor.getY() + 0.5, anchor.getZ() + 0.5), facing.asString());
		}
	}
}
