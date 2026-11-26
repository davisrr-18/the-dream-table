package com.davisr.thedreamtable.screen.page;

import com.davisr.thedreamtable.farm.FarmLoader;
import com.davisr.thedreamtable.farm.FarmListing;
import com.davisr.thedreamtable.farm.FarmStorage;
import com.davisr.thedreamtable.network.FarmDetailsPayload;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FarmPageLayoutTest {
	@Test
	void wrapsTheNinthItemBeforeItLeavesTheFarmCard() {
		assertEquals(147, FarmPage.itemXOffset(7));
		assertEquals(0, FarmPage.itemXOffset(8));
		assertEquals(1, FarmPage.itemRow(8));
		assertEquals(2, FarmPage.itemRows(9));
	}

	/** Favorite, track, Keep Loaded, Repair, then Take Down: one row each, with no hole where a removed row was. */
	@Test
	void aFinishedFarmsButtonsFollowEachOtherWithoutGaps() {
		FarmLoader.Entry farm = new FarmLoader.Entry("overworld|farm", "farm", "", "", "minecraft:overworld", BlockPos.ORIGIN,
				false, false, false, false, FarmStorage.RUNNING, 0, true, 0);

		assertEquals(4, FarmPage.takeDownRow(farm));
		assertEquals(5, FarmPage.confirmRow());
		assertEquals(6, FarmPage.backRow(farm));
	}

	/** A farm the mod no longer has has no Repair, so Forget takes Repair's row and Back follows it. */
	@Test
	void aMissingFarmsForgetAndBackFillTheRowsRepairWouldUse() {
		FarmLoader.Entry farm = new FarmLoader.Entry("overworld|farm", "farm", "", "", "minecraft:overworld", BlockPos.ORIGIN,
				false, false, false, false, FarmStorage.UNLOADED, 0, false, 0);

		assertEquals(3, FarmPage.takeDownRow(farm));
		assertEquals(4, FarmPage.backRow(farm));
	}

	@Test
	void farmSummaryNamesItsOperatingState() throws ReflectiveOperationException {
		FarmLoader.Entry farm = new FarmLoader.Entry("overworld|farm", "farm", "", "", "minecraft:overworld", new BlockPos(10, 64, 20),
				false, false, true, false, FarmStorage.RUNNING, 0, true, 25);

		Text summary = FarmPage.summaryText(farm, new FarmListing.Viewer("minecraft:overworld", new BlockPos(10, 64, 0)));

		assertFalse(keys(summary).stream().anyMatch(key -> key.startsWith("gui.the_dream_table.role.")));
		assertTrue(keys(summary).contains("gui.the_dream_table.status_running_storage"));
		assertTrue(keys(summary).contains("gui.the_dream_table.kept_loaded"));
		assertTrue(keys(summary).contains("gui.the_dream_table.blocks_away"));
	}

	@Test
	void productionLineSaysMeasuringNothingOrHeadsTheList() {
		assertEquals(List.of("gui.the_dream_table.production_measuring"), keys(FarmPage.productionText(details(true, 1, 3, List.of()))));
		assertEquals(List.of("gui.the_dream_table.production_none"), keys(FarmPage.productionText(details(true, 1, 30, List.of()))));
		assertEquals(List.of("gui.the_dream_table.production_title"), keys(FarmPage.productionText(details(true, 1, 30, List.of("minecraft:wheat")))));
		assertEquals(null, FarmPage.productionText(details(false, 0, 30, List.of())));
		assertEquals(null, FarmPage.productionText(details(true, 0, 30, List.of())));
		assertEquals(null, FarmPage.productionText(null));
	}

	private static FarmDetailsPayload details(boolean loaded, int containers, int minutes, List<String> production) {
		return new FarmDetailsPayload("minecraft:overworld|farm", loaded, 0, 54, containers, 96, List.of(), List.of(), 0, 0, List.of(), List.of(),
				production, production.stream().map(item -> 60).toList(), minutes);
	}

	private static List<String> keys(Text text) {
		List<String> keys = new ArrayList<>();
		collectKeys(text, keys);
		return keys;
	}

	private static void collectKeys(Text text, List<String> keys) {
		if (text.getContent() instanceof TranslatableTextContent translated) {
			keys.add(translated.getKey());
			for (Object argument : translated.getArgs()) {
				if (argument instanceof Text nested) {
					collectKeys(nested, keys);
				}
			}
		}
		for (Text sibling : text.getSiblings()) {
			collectKeys(sibling, keys);
		}
	}
}
