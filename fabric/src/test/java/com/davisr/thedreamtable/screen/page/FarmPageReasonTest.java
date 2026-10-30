package com.davisr.thedreamtable.screen.page;

import com.davisr.thedreamtable.GameSetup;
import com.davisr.thedreamtable.farm.FarmLoader;
import com.davisr.thedreamtable.farm.FarmRole;
import com.davisr.thedreamtable.screen.ui.Viewport;
import com.davisr.thedreamtable.screen.ui.layout.ScreenGeometry;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FarmPageReasonTest {
	@Test
	void keepLoadedNamesWhyItIsOff() {
		assertEquals("gui.the_dream_table.reason_operators_only", key(FarmPage.keepLoadedReason(FarmRole.VIEWER, 4, false, false)));
		assertEquals("gui.the_dream_table.reason_keep_loaded_server", key(FarmPage.keepLoadedReason(FarmRole.OWNER, 0, false, false)));
		assertEquals("gui.the_dream_table.loaded_full", key(FarmPage.keepLoadedReason(FarmRole.OWNER, 4, false, true)));
		assertNull(FarmPage.keepLoadedReason(FarmRole.OWNER, 4, false, false), "usable");
		assertNull(FarmPage.keepLoadedReason(FarmRole.OPERATOR, 4, true, true), "a farm already kept loaded can be switched off");
	}

	@Test
	void repairNamesWhyItIsOffInItsOrder() {
		assertEquals("gui.the_dream_table.reason_operators_only", key(FarmPage.repairReason(FarmRole.VIEWER, true, 3)));
		assertEquals("gui.the_dream_table.repair_busy", key(FarmPage.repairReason(FarmRole.OWNER, true, 3)));
		assertEquals("gui.the_dream_table.reason_repair_whole", key(FarmPage.repairReason(FarmRole.OWNER, false, 0)));
		assertNull(FarmPage.repairReason(FarmRole.OWNER, false, 3));
		assertNull(FarmPage.repairReason(FarmRole.OWNER, false, FarmLoader.UNKNOWN), "an unloaded part of the farm still allows Repair");
	}

	@Test
	void everyReasonKeyIsInTheEnglishFile() throws Exception {
		JsonObject lang;
		try (InputStream stream = FarmPageReasonTest.class.getResourceAsStream("/assets/the_dream_table/lang/en_us.json")) {
			assertNotNull(stream);
			lang = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
		}
		for (String key : new String[] {
				"gui.the_dream_table.reason_operators_only", "gui.the_dream_table.reason_keep_loaded_server", "gui.the_dream_table.reason_repair_whole",
				"gui.the_dream_table.reason_type_name", "gui.the_dream_table.reason_type_player", "gui.the_dream_table.item_stored",
				"gui.the_dream_table.item_needed", "gui.the_dream_table.item_supplied", "gui.the_dream_table.pace_slider"}) {
			assertTrue(lang.has(key), key);
		}
	}

	@Test
	void hoveringAnItemIconMarksOnlyThatIconForItsName() {
		GameSetup.bootstrap();
		Viewport view = new Viewport();
		view.layout(ScreenGeometry.calculate(640, 360, 0, false), 0, 300);
		PageContext context = new PageContext(null, view, null, null);
		context.beginControls(7, new SpeedPage(context));
		int x = view.flowX();
		int y = view.cardY(0);

		context.hoverItem(new ItemStack(Items.STONE), Text.literal("Stored: 3"), x, y, x + 4, y + 4);
		assertEquals(Items.STONE, context.hoveredItem().getItem());
		assertEquals("Stored: 3", context.hoveredNote().getString());

		context.beginControls(7, new SpeedPage(context));
		assertTrue(context.hoveredItem().isEmpty(), "a new frame forgets the last hover");
		context.hoverItem(new ItemStack(Items.STONE), Text.empty(), x, y, x + 200, y + 200);
		assertTrue(context.hoveredItem().isEmpty(), "outside the 16 px icon nothing is hovered");
	}

	private static String key(Text text) {
		return text.getContent() instanceof TranslatableTextContent translated ? translated.getKey() : text.getString();
	}
}
