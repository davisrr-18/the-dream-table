package com.davisr.thedreamtable.screen.page;

import com.davisr.thedreamtable.GameSetup;
import com.davisr.thedreamtable.farm.FarmOptions.FarmOption;
import com.davisr.thedreamtable.farm.FarmTemplates;
import com.davisr.thedreamtable.farm.ImportNote;
import com.davisr.thedreamtable.farm.ImportedStructures;
import com.davisr.thedreamtable.screen.ui.Viewport;
import com.davisr.thedreamtable.screen.ui.layout.ScreenGeometry;
import net.minecraft.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.davisr.thedreamtable.screen.ui.Theme.CARD_GAP;
import static com.davisr.thedreamtable.screen.ui.Theme.CARD_HEIGHT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemesPageLayoutTest {
	private List<FarmOption> schemes;

	@BeforeAll
	static void bootstrap() {
		GameSetup.bootstrap();
	}

	@BeforeEach
	void useFourSchemesAndRejectedFiles() {
		schemes = List.of(
				new FarmOption("nbt:one", Items.STONE, "farm.the_dream_table.imported", "One"),
				new FarmOption("nbt:two", Items.STONE, "farm.the_dream_table.imported", "Two"),
				new FarmOption("nbt:three", Items.STONE, "farm.the_dream_table.imported", "Three"),
				new FarmOption("nbt:four", Items.STONE, "farm.the_dream_table.imported", "Four"));
		FarmTemplates.useRemote(schemes, Map.of(), Map.of(), List.of(
				new ImportedStructures.Rejected("bad-one", ImportNote.of(ImportNote.Kind.UNREADABLE)),
				new ImportedStructures.Rejected("bad-two", ImportNote.of(ImportNote.Kind.EMPTY)),
				new ImportedStructures.Rejected("bad-three", ImportNote.of(ImportNote.Kind.TOO_TALL)),
				new ImportedStructures.Rejected("bad-four", ImportNote.of(ImportNote.Kind.TOO_LARGE))));
	}

	@AfterEach
	void restoreLocalSchemes() {
		FarmTemplates.useLocal();
	}

	@Test
	void laysSchemesAndRejectedFilesInTwoRowsPerCollectionInWideMode() {
		Viewport compact = new Viewport();
		compact.layout(ScreenGeometry.calculate(416, 240, 0, false), 0, 0);
		Viewport wide = new Viewport();
		wide.layout(ScreenGeometry.calculate(420, 240, 0, false), 0, 0);
		SchemesPage compactPage = new SchemesPage(new PageContext(null, compact, null, null));
		SchemesPage widePage = new SchemesPage(new PageContext(null, wide, null, null));

		int compactHeight = compactPage.contentHeight();
		int wideHeight = widePage.contentHeight();

		assertTrue(wideHeight < compactHeight);
		assertEquals(4 * (CARD_HEIGHT + CARD_GAP), compactHeight - wideHeight);
		assertEquals(wide.gridCardX(0), SchemesPage.cardX(wide, 0));
		assertEquals(wide.gridCardX(1), SchemesPage.cardX(wide, 1));
		assertEquals(SchemesPage.cardY(wide, 0), SchemesPage.cardY(wide, 1));
		assertTrue(SchemesPage.cardY(wide, 2) > SchemesPage.cardY(wide, 1));
		assertTrue(SchemesPage.rejectedY(wide, 0, 4) > SchemesPage.rejectedNoteY(wide, 4));
		assertEquals(SchemesPage.rejectedY(wide, 0, 4), SchemesPage.rejectedY(wide, 1, 4));
		assertTrue(SchemesPage.rejectedY(wide, 2, 4) > SchemesPage.rejectedY(wide, 1, 4));
	}
}
