package com.davisr.thedreamtable.screen.page;

import com.davisr.thedreamtable.farm.FarmLoader;
import com.davisr.thedreamtable.farm.FarmListing;
import com.davisr.thedreamtable.farm.FarmRole;
import com.davisr.thedreamtable.farm.FarmStorage;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FarmPageLayoutTest {
	@Test
	void wrapsTheNinthItemBeforeItLeavesTheFarmCard() {
		assertEquals(147, FarmPage.itemXOffset(7));
		assertEquals(0, FarmPage.itemXOffset(8));
		assertEquals(1, FarmPage.itemRow(8));
		assertEquals(2, FarmPage.itemRows(9));
	}

	@Test
	void farmSummaryNamesTheRoleAlongsideItsOperatingState() throws ReflectiveOperationException {
		FarmLoader.Entry farm = new FarmLoader.Entry("overworld|farm", "farm", "", "", "minecraft:overworld", new BlockPos(10, 64, 20),
				FarmRole.OPERATOR, false, false, true, false, FarmStorage.RUNNING, 0, true, 25);

		Text summary = FarmPage.summaryText(farm, new FarmListing.Viewer("minecraft:overworld", new BlockPos(10, 64, 0)));

		assertTrue(keys(summary).contains("gui.the_dream_table.role.operator"));
		assertTrue(keys(summary).contains("gui.the_dream_table.status_running_storage"));
		assertTrue(keys(summary).contains("gui.the_dream_table.kept_loaded"));
		assertTrue(keys(summary).contains("gui.the_dream_table.blocks_away"));
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
