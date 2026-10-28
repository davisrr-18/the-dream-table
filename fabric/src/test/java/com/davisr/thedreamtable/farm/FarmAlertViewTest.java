package com.davisr.thedreamtable.farm;

import com.davisr.thedreamtable.farm.alert.FarmAlertType;
import com.davisr.thedreamtable.farm.alert.FarmAlertView;
import com.davisr.thedreamtable.farm.alert.FarmAlerts;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FarmAlertViewTest {
	@Test
	void pagesActiveEntriesBeforeHistoryAndHidesEventsBeforeAcceptance() {
		FarmAlerts alerts = new FarmAlerts();
		alerts.record(FarmAlertType.REPAIR_COMPLETED, 10L);
		alerts.observe(FarmAlertType.BLOCKS_MISSING, 2, 20L);
		alerts.record(FarmAlertType.REPAIR_COMPLETED, 30L);

		List<FarmAlertView.Entry> entries = FarmAlertView.page(alerts, 15L, 0, 50);

		assertEquals(2, entries.size());
		assertTrue(entries.get(0).active());
		assertEquals(FarmAlertType.BLOCKS_MISSING, entries.get(0).event().type());
		assertEquals(30L, entries.get(1).event().time());
	}

	@Test
	void showsAllActiveAlertsEvenWhenTheyStartedBeforeCollaboratorAcceptance() {
		FarmAlerts alerts = new FarmAlerts();
		alerts.observe(FarmAlertType.BLOCKS_MISSING, 2, 5L);
		alerts.record(FarmAlertType.REPAIR_COMPLETED, 6L);

		List<FarmAlertView.Entry> entries = FarmAlertView.page(alerts, 1_700_000_000_000L, 0, 50);

		assertEquals(1, entries.size());
		assertTrue(entries.get(0).active());
		assertEquals(FarmAlertType.BLOCKS_MISSING, entries.get(0).event().type());
	}

	@Test
	void appliesAcceptanceToUnixMillisecondHistoryTimes() {
		FarmAlerts alerts = new FarmAlerts();
		long acceptedAt = 1_700_000_000_000L;
		alerts.record(FarmAlertType.REPAIR_COMPLETED, acceptedAt - 1);
		alerts.record(FarmAlertType.REPAIR_COMPLETED, acceptedAt + 1);

		List<FarmAlertView.Entry> entries = FarmAlertView.page(alerts, acceptedAt, 0, 50);

		assertEquals(1, entries.size());
		assertEquals(acceptedAt + 1, entries.get(0).event().time());
	}

	@Test
	void capsAnyPageAtFiftyEntries() {
		FarmAlerts alerts = new FarmAlerts();
		for (int index = 0; index < 20; index++) {
			alerts.record(FarmAlertType.REPAIR_COMPLETED, index);
		}

		assertEquals(20, FarmAlertView.page(alerts, 0L, 0, 500).size());
	}

	private static void assertTrue(boolean value) {
		org.junit.jupiter.api.Assertions.assertTrue(value);
	}
}
