package com.davisr.thedreamtable.farm.schematic;

import com.davisr.thedreamtable.GameSetup;
import com.davisr.thedreamtable.farm.FarmStep;
import com.davisr.thedreamtable.farm.ImportNote;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtInt;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What the table says about schematic files before anyone builds them. */
class SchematicValidationTest {
	@TempDir
	Path folder;

	@BeforeAll
	static void setUp() {
		GameSetup.bootstrap();
	}

	/** A structure block file with these palette names and blocks as {x, y, z, palette index}. */
	private static NbtCompound structure(List<String> palette, int[]... blocks) {
		NbtCompound root = new NbtCompound();
		NbtList names = new NbtList();
		for (String name : palette) {
			NbtCompound entry = new NbtCompound();
			entry.putString("Name", name);
			names.add(entry);
		}
		root.put("palette", names);
		NbtList list = new NbtList();
		for (int[] block : blocks) {
			NbtCompound entry = new NbtCompound();
			NbtList pos = new NbtList();
			pos.add(NbtInt.of(block[0]));
			pos.add(NbtInt.of(block[1]));
			pos.add(NbtInt.of(block[2]));
			entry.put("pos", pos);
			entry.putInt("state", block[3]);
			list.add(entry);
		}
		root.put("blocks", list);
		return root;
	}

	private SchematicReader.Result read(NbtCompound root) throws IOException {
		Path file = folder.resolve("farm.nbt");
		NbtIo.writeCompressed(root, file);
		return SchematicReader.read("nbt:farm", file);
	}

	private static ImportNote.Kind only(SchematicReader.Result result) {
		assertEquals(1, result.notes().size());
		return result.notes().get(0).kind();
	}

	@Test
	void aPlainFileHasNoNotes() throws IOException {
		SchematicReader.Result result = read(structure(List.of("minecraft:stone", "minecraft:hopper"), new int[]{0, 0, 0, 0}, new int[]{0, 1, 0, 1}));
		assertNotNull(result.template());
		assertEquals(2, result.template().steps().size());
		assertTrue(result.notes().isEmpty());
	}

	@Test
	void blocksFromMissingModsAreLeftOutAndNamed() throws IOException {
		SchematicReader.Result result = read(structure(List.of("minecraft:stone", "somemod:widget"),
				new int[]{0, 0, 0, 0}, new int[]{1, 0, 0, 1}, new int[]{2, 0, 0, 1}, new int[]{3, 0, 0, 1}));
		assertEquals(1, result.template().steps().size());
		assertEquals(ImportNote.Kind.UNKNOWN_BLOCKS, only(result));
		assertEquals(3, result.notes().get(0).count());
		assertEquals(List.of("somemod:widget"), result.notes().get(0).names());
	}

	@Test
	void blocksOnlyCreativeCanGetAreNamed() throws IOException {
		SchematicReader.Result result = read(structure(List.of("minecraft:bedrock", "minecraft:infested_stone"), new int[]{0, 0, 0, 0}, new int[]{1, 0, 0, 1}));
		assertNotNull(result.template());
		assertEquals(ImportNote.Kind.CREATIVE_ONLY, only(result));
		assertEquals(List.of("minecraft:bedrock", "minecraft:infested_stone"), result.notes().get(0).names());
	}

	@Test
	void onlyAirIsEmpty() throws IOException {
		SchematicReader.Result result = read(structure(List.of("minecraft:air"), new int[]{0, 0, 0, 0}));
		assertNull(result.template());
		assertEquals(ImportNote.Kind.EMPTY, only(result));
	}

	@Test
	void tallerThanAnyWorldIsRejected() throws IOException {
		SchematicReader.Result result = read(structure(List.of("minecraft:stone"), new int[]{0, 0, 0, 0}, new int[]{0, 400, 0, 0}));
		assertNull(result.template());
		assertEquals(ImportNote.Kind.TOO_TALL, only(result));
		assertEquals(401, result.notes().get(0).count());
	}

	@Test
	void tooManyBlocksIsRejectedWithTheCount() throws IOException {
		int count = SchematicReader.MAX_BLOCKS + 1;
		int[][] blocks = new int[count][];
		for (int index = 0; index < count; index++) {
			blocks[index] = new int[]{index % 128, index / (128 * 128), (index / 128) % 128, 0};
		}
		SchematicReader.Result result = read(structure(List.of("minecraft:stone"), blocks));
		assertNull(result.template());
		assertEquals(ImportNote.Kind.TOO_MANY_BLOCKS, only(result));
		assertEquals(count, result.notes().get(0).count());
	}

	@Test
	void damagedFilesAreUnreadable() throws IOException {
		Path file = folder.resolve("broken.schem");
		Files.write(file, new byte[]{1, 2, 3, 4, 5, 6, 7, 8});
		SchematicReader.Result result = SchematicReader.read("nbt:broken", file);
		assertNull(result.template());
		assertEquals(ImportNote.Kind.UNREADABLE, only(result));
	}

	@Test
	void nbtWithoutBlocksIsAnUnknownFormat() throws IOException {
		NbtCompound root = new NbtCompound();
		root.putString("Author", "Davi");
		SchematicReader.Result result = read(root);
		assertNull(result.template());
		assertEquals(ImportNote.Kind.UNKNOWN_FORMAT, only(result));
	}

	@Test
	void litematicaTileDataReachesItsBlock() throws IOException {
		NbtCompound region = new NbtCompound();
		region.putIntArray("Position", new int[]{0, 0, 0});
		region.putIntArray("Size", new int[]{1, 2, 1});
		NbtList palette = new NbtList();
		NbtCompound air = new NbtCompound();
		air.putString("Name", "minecraft:air");
		NbtCompound chest = new NbtCompound();
		chest.putString("Name", "minecraft:chest");
		palette.add(air);
		palette.add(chest);
		region.put("BlockStatePalette", palette);
		region.putLongArray("BlockStates", new long[]{1L});
		NbtList tiles = new NbtList();
		NbtCompound tile = new NbtCompound();
		tile.putInt("x", 0);
		tile.putInt("y", 0);
		tile.putInt("z", 0);
		tile.putString("CustomName", "\"Loot\"");
		tiles.add(tile);
		region.put("TileEntities", tiles);
		NbtCompound regions = new NbtCompound();
		regions.put("Main", region);
		NbtCompound root = new NbtCompound();
		root.put("Regions", regions);
		SchematicReader.Result result = read(root);
		FarmStep step = result.template().steps().get(0);
		assertNotNull(step.blockNbt());
		assertEquals("\"Loot\"", step.blockNbt().getString("CustomName"));
	}

	@Test
	void bigFilesWithManyContainersReadQuickly() {
		int count = SchematicReader.MAX_BLOCKS;
		NbtCompound root = structure(List.of("minecraft:chest"));
		NbtList blocks = root.getList("blocks", 10);
		for (int index = 0; index < count; index++) {
			NbtCompound entry = new NbtCompound();
			NbtList pos = new NbtList();
			pos.add(NbtInt.of(index % 128));
			pos.add(NbtInt.of(index / (128 * 128)));
			pos.add(NbtInt.of((index / 128) % 128));
			entry.put("pos", pos);
			entry.putInt("state", 0);
			entry.put("nbt", new NbtCompound());
			blocks.add(entry);
		}
		assertTimeoutPreemptively(Duration.ofSeconds(5), () -> assertEquals(count, read(root).template().steps().size()));
	}
}
