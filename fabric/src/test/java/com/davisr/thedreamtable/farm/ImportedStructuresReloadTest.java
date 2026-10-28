package com.davisr.thedreamtable.farm;

import com.davisr.thedreamtable.GameSetup;
import com.davisr.thedreamtable.platform.Platform;
import com.davisr.thedreamtable.platform.PlatformPaths;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtInt;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class ImportedStructuresReloadTest {
	@TempDir
	Path game;
	Path schematics;

	@BeforeAll
	static void setUp() {
		GameSetup.bootstrap();
	}

	@AfterAll
	static void uninstall() {
		Platform.install(null, null, null, null, null, null, null);
	}

	@BeforeEach
	void install() throws IOException {
		Path gameDir = game;
		Platform.install(new PlatformPaths() {
			@Override
			public Path configDir() {
				return gameDir.resolve("config");
			}

			@Override
			public Path gameDir() {
				return gameDir;
			}
		}, null, null, null, null, null, null);
		schematics = Files.createDirectories(game.resolve("schematics"));
	}

	private static NbtCompound stone() {
		NbtCompound root = new NbtCompound();
		NbtList names = new NbtList();
		NbtCompound entry = new NbtCompound();
		entry.putString("Name", "minecraft:stone");
		names.add(entry);
		root.put("palette", names);
		NbtList blocks = new NbtList();
		NbtCompound block = new NbtCompound();
		NbtList pos = new NbtList();
		pos.add(NbtInt.of(0));
		pos.add(NbtInt.of(0));
		pos.add(NbtInt.of(0));
		block.put("pos", pos);
		block.putInt("state", 0);
		blocks.add(block);
		root.put("blocks", blocks);
		return root;
	}

	private static void write(Path folder, String name) throws IOException {
		Files.createDirectories(folder);
		NbtIo.writeCompressed(stone(), folder.resolve(name));
	}

	private static List<String> ids(ImportedStructures.Imports imports) {
		return imports.options().stream().map(FarmOptions.FarmOption::id).toList();
	}

	@Test
	void normalizedNameCollisionsGetNumberedSuffixesInFileNameOrder() throws IOException {
		write(schematics, "my_farm.nbt");
		write(schematics, "My Farm.nbt");
		write(schematics, "MY-FARM!.nbt");

		ImportedStructures.Imports imports = ImportedStructures.reload(null);

		// Sorted by lower-cased file name: "my farm", "my-farm!", "my_farm".
		assertEquals(List.of("nbt:my_farm", "nbt:my_farm_2", "nbt:my_farm_3"), ids(imports));
		assertEquals(3, imports.templates().size());
		assertEquals("nbt:my_farm_2", imports.templates().get("nbt:my_farm_2").id());
	}

	@Test
	void theOrderIsStableAcrossReloads() throws IOException {
		write(schematics, "b.nbt");
		write(schematics, "a.nbt");

		List<String> first = ids(ImportedStructures.reload(null));

		assertEquals(List.of("nbt:a", "nbt:b"), first);
		assertEquals(first, ids(ImportedStructures.reload(null)));
	}

	@Test
	void sameFileNameInTwoFoldersLoadsOnceFromTheFirstFolder() throws IOException {
		write(schematics, "same.nbt");
		write(game.resolve("config").resolve("the_dream_table").resolve("structures"), "SAME.nbt");

		assertEquals(List.of("nbt:same"), ids(ImportedStructures.reload(null)));
	}

	@Test
	void unusableFilesAreListedAsRejectedAndTakeNoId() throws IOException {
		write(schematics, "good.nbt");
		NbtIo.writeCompressed(new NbtCompound(), schematics.resolve("empty.nbt"));
		Files.writeString(schematics.resolve("garbage.nbt"), "not nbt at all");

		ImportedStructures.Imports imports = ImportedStructures.reload(null);

		assertEquals(List.of("nbt:good"), ids(imports));
		assertEquals(List.of("empty", "garbage"), imports.rejected().stream().map(ImportedStructures.Rejected::name).toList());
	}

	@Test
	void anUnchangedFileIsNotParsedAgainButAChangedOneIs() throws IOException {
		write(schematics, "cached.nbt");
		Path file = schematics.resolve("cached.nbt");
		FarmTemplate first = ImportedStructures.reload(null).templates().get("nbt:cached");

		assertSame(first, ImportedStructures.reload(null).templates().get("nbt:cached"));

		Files.setLastModifiedTime(file, FileTime.fromMillis(Files.getLastModifiedTime(file).toMillis() + 5_000));
		assertNotSame(first, ImportedStructures.reload(null).templates().get("nbt:cached"));
	}

	@Test
	void aFilesVersionChangesOnlyWhenTheFileDoes() throws IOException {
		write(schematics, "versioned.nbt");
		Path file = schematics.resolve("versioned.nbt");
		long first = ImportedStructures.reload(null).versions().get("nbt:versioned");

		assertEquals(first, ImportedStructures.reload(null).versions().get("nbt:versioned"));

		Files.setLastModifiedTime(file, FileTime.fromMillis(Files.getLastModifiedTime(file).toMillis() + 5_000));
		assertNotEquals(first, ImportedStructures.reload(null).versions().get("nbt:versioned"));
	}
}
