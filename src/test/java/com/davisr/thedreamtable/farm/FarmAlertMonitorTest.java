package com.davisr.thedreamtable.farm;

import com.davisr.thedreamtable.farm.alert.FarmAlertMonitor;
import com.davisr.thedreamtable.farm.alert.FarmAlertType;
import com.davisr.thedreamtable.farm.alert.FarmAlerts;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FarmAlertMonitorTest {
	@Test
	void loadedObservationCreatesAndResolvesHealthAlerts() {
		FarmAlerts alerts = new FarmAlerts();

		FarmAlertMonitor.observe(alerts, new FarmAlertMonitor.Observation(true, true, FarmStorage.FULL, 3), 100L);
		assertTrue(alerts.active(FarmAlertType.STORAGE_FULL).isPresent());
		assertEqualsQuantity(alerts, FarmAlertType.BLOCKS_MISSING, 3);

		FarmAlertMonitor.observe(alerts, new FarmAlertMonitor.Observation(true, true, FarmStorage.RUNNING, 0), 101L);
		assertFalse(alerts.active(FarmAlertType.STORAGE_FULL).isPresent());
		assertFalse(alerts.active(FarmAlertType.BLOCKS_MISSING).isPresent());
	}

	@Test
	void unloadedObservationNeverResolvesExistingAlerts() {
		FarmAlerts alerts = new FarmAlerts();
		alerts.observe(FarmAlertType.BLOCKS_MISSING, 2, 100L);

		FarmAlertMonitor.observe(alerts, new FarmAlertMonitor.Observation(false, true, FarmStorage.UNLOADED, FarmLoader.UNKNOWN), 101L);

		assertEqualsQuantity(alerts, FarmAlertType.BLOCKS_MISSING, 2);
	}

	@Test
	void unavailableDesignIsReportedWithoutPretendingToKnowWorldHealth() {
		FarmAlerts alerts = new FarmAlerts();

		FarmAlertMonitor.observe(alerts, new FarmAlertMonitor.Observation(true, false, FarmStorage.UNLOADED, FarmLoader.UNKNOWN), 100L);

		assertTrue(alerts.active(FarmAlertType.DESIGN_UNAVAILABLE).isPresent());
		assertFalse(alerts.active(FarmAlertType.STORAGE_FULL).isPresent());
		assertFalse(alerts.active(FarmAlertType.BLOCKS_MISSING).isPresent());
	}

	private static void assertEqualsQuantity(FarmAlerts alerts, FarmAlertType type, int quantity) {
		org.junit.jupiter.api.Assertions.assertEquals(quantity, alerts.active(type).orElseThrow().quantity());
	}
}
