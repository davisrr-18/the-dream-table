package com.davisr.thedreamtable.command;

import com.davisr.thedreamtable.farm.FarmAreas;
import com.davisr.thedreamtable.farm.FarmLoader;
import com.davisr.thedreamtable.farm.ImportNote;
import com.davisr.thedreamtable.farm.ImportedStructures;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The admin commands print translatable lines whose numbers come straight from the mod's state. */
class DreamTableCommandsTest {
	private static TranslatableTextContent content(Text text) {
		return (TranslatableTextContent) text.getContent();
	}

	@Test
	void worldsAreAddedUp() {
		FarmAreas.Stats first = new FarmAreas.Stats(2, 1, 1, 0, 5, 0);
		FarmAreas.Stats second = new FarmAreas.Stats(1, 0, 0, 2, 3, 1);
		assertEquals(new FarmAreas.Stats(3, 1, 1, 2, 8, 1), first.plus(second));
	}

	@Test
	void importCountsSeparateLoadedRejectedAndWarnings() {
		ImportedStructures.Imports imports = new ImportedStructures.Imports(Map.of(), List.of(),
				Map.of("nbt:a", List.of(ImportNote.of(ImportNote.Kind.TABLE_BLOCKS), ImportNote.of(ImportNote.Kind.CREATIVE_ONLY)),
						"nbt:b", List.of(ImportNote.of(ImportNote.Kind.UNKNOWN_BLOCKS))),
				List.of(new ImportedStructures.Rejected("bad", ImportNote.of(ImportNote.Kind.UNREADABLE))), Map.of());
		assertEquals(0, imports.loaded());
		assertEquals(3, imports.warningCount());
		assertEquals(1, imports.rejected().size());
	}

	@Test
	void anUnchangedListingIsNotAChange() {
		ImportedStructures.Imports first = new ImportedStructures.Imports(Map.of(), List.of(), Map.of(), List.of(), Map.of("nbt:a", 1L));
		ImportedStructures.Imports same = new ImportedStructures.Imports(Map.of(), List.of(), Map.of(), List.of(), Map.of("nbt:a", 1L));
		ImportedStructures.Imports edited = new ImportedStructures.Imports(Map.of(), List.of(), Map.of(), List.of(), Map.of("nbt:a", 2L));
		assertTrue(first.sameListing(same));
		assertTrue(!first.sameListing(edited));
	}

	@Test
	void reloadReportsTheThreeCounts() {
		ImportedStructures.Imports imports = new ImportedStructures.Imports(Map.of(), List.of(),
				Map.of("nbt:a", List.of(ImportNote.of(ImportNote.Kind.TABLE_BLOCKS))),
				List.of(new ImportedStructures.Rejected("bad", ImportNote.of(ImportNote.Kind.EMPTY))), Map.of());
		TranslatableTextContent line = content(DreamTableCommands.reloaded(imports));
		assertEquals("gui.the_dream_table.command_reload_done", line.getKey());
		assertArrayEquals(new Object[]{0, 1, 1}, line.getArgs());
	}

	@Test
	void statusPrintsEveryFigureOnItsOwnLine() {
		FarmAreas.Stats areas = new FarmAreas.Stats(4, 2, 1, 1, 9, 3);
		FarmLoader.Status loader = new FarmLoader.Status(5, 2, 18);
		ImportedStructures.Imports imports = new ImportedStructures.Imports(Map.of(), List.of(), Map.of(), List.of(), Map.of());

		List<Text> lines = DreamTableCommands.statusLines(areas, loader, imports);

		assertEquals(7, lines.size());
		assertEquals("gui.the_dream_table.command_status_header", content(lines.get(0)).getKey());
		assertArrayEquals(new Object[]{4}, content(lines.get(1)).getArgs());
		assertArrayEquals(new Object[]{2, 1, 1}, content(lines.get(2)).getArgs());
		assertArrayEquals(new Object[]{9}, content(lines.get(3)).getArgs());
		assertArrayEquals(new Object[]{2, 18, 5}, content(lines.get(4)).getArgs());
		assertArrayEquals(new Object[]{3}, content(lines.get(5)).getArgs());
		assertArrayEquals(new Object[]{0, 0, 0}, content(lines.get(6)).getArgs());
		assertTrue(lines.stream().allMatch(line -> content(line).getKey().startsWith("gui.the_dream_table.command_status_")));
	}
}
