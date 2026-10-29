package com.davisr.thedreamtable.screen.page;

import com.davisr.thedreamtable.GameSetup;
import com.davisr.thedreamtable.farm.FarmLoader;
import com.davisr.thedreamtable.farm.FarmRole;
import com.davisr.thedreamtable.network.FarmAlertsPayload;
import com.davisr.thedreamtable.screen.ui.Theme;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** My Farms shows alerts as a red sign before the farm's name and red "Alert:" lines last in its details. */
class AlertTextTest {
	@BeforeAll
	static void bootstrap() {
		GameSetup.bootstrap();
	}

	private static FarmLoader.Entry farm(String key, int alerts) {
		return new FarmLoader.Entry(key, "farm", "", "", "minecraft:overworld", BlockPos.ORIGIN, FarmRole.OWNER,
				false, false, false, false, 0, 0, true, 0, alerts);
	}

	@Test
	void aFarmWithAnAlertIsMarkedBeforeItsNameInRed() {
		Text name = Text.literal("Iron farm");
		assertEquals("Iron farm", AlertText.listName(farm("a", 0), name).getString());
		Text marked = AlertText.listName(farm("a", 2), name);
		assertEquals(AlertText.SIGN + " Iron farm", marked.getString());
		assertEquals(Theme.ERROR & 0xFFFFFF, marked.getStyle().getColor().getRgb());
	}

	@Test
	void anAlertLineIsRedAndNamesTheAlertAndItsQuantity() {
		Text line = AlertText.line("BLOCKS_MISSING", 12);
		TranslatableTextContent content = (TranslatableTextContent) line.getContent();
		assertEquals("gui.the_dream_table.alert_line", content.getKey());
		assertEquals(Theme.ERROR & 0xFFFFFF, line.getStyle().getColor().getRgb());
		TranslatableTextContent inner = (TranslatableTextContent) ((Text) content.getArgs()[0]).getContent();
		assertEquals("gui.the_dream_table.alert.blocks_missing", inner.getKey());
		assertEquals(12, inner.getArgs()[0]);
	}

	@Test
	void onlyActiveAlertsOfTheSameFarmAreListed() {
		FarmAlertsPayload page = new FarmAlertsPayload("a", "token", List.of(
				new FarmAlertsPayload.Entry(1, "STORAGE_FULL", 1, 0, true),
				new FarmAlertsPayload.Entry(2, "REPAIR_COMPLETED", 1, 0, false),
				new FarmAlertsPayload.Entry(3, "BLOCKS_MISSING", 4, 0, true)));

		assertEquals(2, AlertText.lines(farm("a", 2), page).size());
		assertTrue(AlertText.lines(farm("b", 2), page).isEmpty(), "a page for another farm");
		assertTrue(AlertText.lines(farm("a", 0), page).isEmpty(), "the list says no alert is active");
		assertTrue(AlertText.lines(farm("a", 2), null).isEmpty(), "still loading");
	}
}
