package com.davisr.thedreamtable.farm;

import com.davisr.thedreamtable.farm.alert.FarmAlertType;
import com.davisr.thedreamtable.farm.alert.FarmAlertView;
import com.davisr.thedreamtable.farm.alert.FarmAlerts;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FarmAlertViewTest {
	@Test
	void pagesActiveEntriesBeforeHistory() {
		FarmAlerts alerts = new FarmAlerts();
		alerts.record(FarmAlertType.REPAIR_COMPLETED, 10L);
		alerts.observe(FarmAlertType.BLOCKS_MISSING, 2, 20L);
		alerts.record(FarmAlertType.REPAIR_COMPLETED, 30L);

		List<FarmAlertView.Entry> entries = FarmAlertView.page(alerts, 0, 50);

		assertEquals(3, entries.size());
		assertTrue(entries.get(0).active());
		assertEquals(FarmAlertType.BLOCKS_MISSING, entries.get(0).event().type());
		// History is newest first.
		assertEquals(30L, entries.get(1).event().time());
		assertEquals(10L, entries.get(2).event().time());
	}

	@Test
	void pagesFromTheOffsetAndStopsAtTheLimit() {
		FarmAlerts alerts = new FarmAlerts();
		alerts.record(FarmAlertType.REPAIR_COMPLETED, 1L);
		alerts.record(FarmAlertType.REPAIR_COMPLETED, 2L);
		alerts.record(FarmAlertType.REPAIR_COMPLETED, 3L);

		List<FarmAlertView.Entry> entries = FarmAlertView.page(alerts, 1, 1);

		assertEquals(1, entries.size());
		assertEquals(2L, entries.get(0).event().time());
	}

	@Test
	void capsAnyPageAtFiftyEntries() {
		FarmAlerts alerts = new FarmAlerts();
		for (int index = 0; index < 20; index++) {
			alerts.record(FarmAlertType.REPAIR_COMPLETED, index);
		}

		assertEquals(20, FarmAlertView.page(alerts, 0, 500).size());
	}

	private static void assertTrue(boolean value) {
		org.junit.jupiter.api.Assertions.assertTrue(value);
	}
}
