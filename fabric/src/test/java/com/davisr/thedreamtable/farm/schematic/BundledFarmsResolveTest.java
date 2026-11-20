package com.davisr.thedreamtable.farm.schematic;

import com.davisr.thedreamtable.DataFixes;
import com.davisr.thedreamtable.GameSetup;
import com.davisr.thedreamtable.farm.ImportNote;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bundled farms were made on 1.21.1. A block the game renamed since (1.21.9: {@code minecraft:chain} became
 * {@code minecraft:iron_chain}) would read as an unknown block and be left out of the farm, so every id must resolve
 * on this game, through the data fixers.
 */
class BundledFarmsResolveTest {
	private static final String[] FILES = {"sugar_cane_farm", "bamboo_farm", "cactus_farm", "crop_farm", "honey_farm", "mob_farm", "creeper_farm", "iron_farm", "gold_farm", "animal_farm"};

	@BeforeAll
	static void setUp() {
		GameSetup.bootstrap();
	}

	private static SchematicReader.Result read(String file) throws IOException {
		try (InputStream stream = BundledFarmsResolveTest.class.getResourceAsStream("/data/the_dream_table/structures/" + file + ".schem")) {
			assertNotNull(stream, file + " is missing");
			return SchematicReader.fromRoot(file, NbtIo.readCompressed(stream, NbtSizeTracker.ofUnlimitedBytes()), false);
		}
	}

	@Test
	void noBundledFarmLosesABlockOrAnEntityToAnUnknownId() throws IOException {
		for (String file : FILES) {
			SchematicReader.Result result = read(file);
			assertNotNull(result.template(), file);
			for (ImportNote note : result.notes()) {
				assertTrue(note.kind() != ImportNote.Kind.UNKNOWN_BLOCKS, file + " has blocks this game does not know: " + note.names());
			}
		}
	}

	@Test
	void aRenamedBlockOfAnOlderFileIsUpdatedToTheCurrentName() {
		NbtCompound chain = new NbtCompound();
		chain.putString("Name", "minecraft:chain");
		NbtCompound properties = new NbtCompound();
		properties.putString("axis", "y");
		chain.put("Properties", properties);

		// Read as a 1.21.1 file, the old name resolves to the current block with its properties kept.
		assertEquals("minecraft:iron_chain", DataFixes.blockState(chain, DataFixes.ASSUMED).getString("Name", ""));
		assertEquals("y", DataFixes.blockState(chain, DataFixes.ASSUMED).getCompoundOrEmpty("Properties").getString("axis", ""));
		assertEquals(Blocks.IRON_CHAIN.getDefaultState().getBlock(), NbtReading.state(chain, DataFixes.ASSUMED).getBlock());
	}

	@Test
	void aNameAlreadyCurrentPassesThroughUnchanged() {
		NbtCompound current = new NbtCompound();
		current.putString("Name", "minecraft:iron_chain");

		assertEquals(current, DataFixes.blockState(current, DataFixes.ASSUMED));
		assertEquals(current, DataFixes.blockState(current, DataFixes.current()));
		assertEquals("minecraft:sugar_cane", DataFixes.itemName("minecraft:sugar_cane", DataFixes.ASSUMED));
		assertEquals("minecraft:iron_chain", DataFixes.itemName("minecraft:chain", DataFixes.ASSUMED));
		assertEquals("a_mod:unknown", DataFixes.itemName("a_mod:unknown", DataFixes.ASSUMED));
	}
}
