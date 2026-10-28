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
	private static final String[] FILES = {"sugar_cane_farm", "bamboo_farm", "cactus_farm", "crop_farm", "honey_farm", "mob_farm", "iron_farm"};

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
			long withItem = java.util.stream.IntStream.range(0, farm.steps().size())
					.filter(index -> farm.steps().get(index).item() != null && !FarmGrowth.isGrowth(farm, index))
					.count();
			int listed = farm.materials().stream().mapToInt(MaterialSpec::count).sum();
			assertEquals(withItem, listed, file);
		}
	}

	@Test
	void everyFarmIsInExactlyOneCategory() {
		for (FarmOptions.FarmOption option : FarmOptions.ALL) {
			long categories = java.util.Arrays.stream(FarmOptions.Category.values()).filter(category -> category.farms().contains(option)).count();
			assertEquals(1, categories, option.id());
		}
		int listed = java.util.Arrays.stream(FarmOptions.Category.values()).mapToInt(category -> category.farms().size()).sum();
		assertEquals(FarmOptions.ALL.size(), listed);
	}

	@Test
	void farmsSurviveTheTripToClients() throws IOException {
		for (String file : FILES) {
			FarmTemplate farm = read(file);
			FarmTemplate copy = TemplateCodec.decode(farm.id(), TemplateCodec.encode(farm));
			assertEquals(farm.steps().size(), copy.steps().size(), file);
			for (int index = 0; index < farm.steps().size(); index++) {
				FarmStep original = farm.steps().get(index);
				FarmStep sent = copy.steps().get(index);
				assertEquals(original.state(), sent.state(), file + " step " + index);
				assertEquals(original.item(), sent.item(), file + " step " + index);
				assertEquals(original.x() + "," + original.y() + "," + original.z(), sent.x() + "," + sent.y() + "," + sent.z(), file + " step " + index);
			}
			assertEquals(farm.materials(), copy.materials(), file);
			assertEquals(farm.signature(), copy.signature(), file);
			assertEquals(farm.center(), copy.center(), file);
		}
	}

	@Test
	void grownCaneIsGrowthButItsBaseIsNot() throws IOException {
		FarmTemplate cane = read("sugar_cane_farm");
		int grown = 0;
		for (int index = 0; index < cane.steps().size(); index++) {
			FarmStep step = cane.steps().get(index);
			if (!step.isEntity() && step.state() != null && step.state().isOf(net.minecraft.block.Blocks.SUGAR_CANE)) {
				boolean onMud = cane.steps().stream().anyMatch(below -> below.x() == step.x() && below.y() == step.y() - 1 && below.z() == step.z()
						&& below.state() != null && below.state().isOf(net.minecraft.block.Blocks.MUD));
				assertEquals(!onMud, FarmGrowth.isGrowth(cane, index), "cane at " + step.x() + "," + step.y());
				grown += onMud ? 0 : 1;
			}
		}
		assertTrue(grown > 0, "the sugar cane farm has grown cane on top");
	}

	@Test
	void everyFarmHasItsTemplate() {
		for (FarmOptions.FarmOption option : FarmOptions.ALL) {
			assertNotNull(FarmTemplates.get(option.id()), option.id());
		}
	}

	@Test
	void cactusIsAPlantFarmAndHoneyIsAResourceFarm() {
		FarmOptions.FarmOption cactus = FarmOptions.byId("cactus").orElseThrow();
		FarmOptions.FarmOption honey = FarmOptions.byId("honey").orElseThrow();

		assertTrue(FarmOptions.Category.PLANTS.farms().contains(cactus));
		assertTrue(FarmOptions.Category.RESOURCES.farms().contains(honey));
		assertNotNull(FarmTemplates.get("cactus"));
		assertNotNull(FarmTemplates.get("honey"));
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
