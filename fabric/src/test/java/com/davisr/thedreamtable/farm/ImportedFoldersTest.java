package com.davisr.thedreamtable.farm;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The order of the schematic folders is the precedence of same-named files, and My Schematics' help text describes it. */
class ImportedFoldersTest {
	private static final Path GAME = Path.of("game");
	private static final Path WORLD = Path.of("world");

	@Test
	void foldersAreReadInTheOrderTheHelpDescribes() {
		assertEquals(List.of(
				GAME.resolve("schematics"),
				GAME.resolve("config").resolve("worldedit").resolve("schematics"),
				GAME.resolve("config").resolve("litematica"),
				WORLD.resolve("schematics"),
				GAME.resolve("config").resolve("the_dream_table").resolve("structures")), ImportedStructures.folders(GAME, WORLD));
	}

	@Test
	void theWorldFolderIsSkippedWithoutAWorld() {
		assertEquals(List.of(
				GAME.resolve("schematics"),
				GAME.resolve("config").resolve("worldedit").resolve("schematics"),
				GAME.resolve("config").resolve("litematica"),
				GAME.resolve("config").resolve("the_dream_table").resolve("structures")), ImportedStructures.folders(GAME, null));
	}
}
