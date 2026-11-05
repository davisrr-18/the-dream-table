package com.davisr.thedreamtable.farm;

import com.davisr.thedreamtable.farm.alert.FarmAlertType;
import com.davisr.thedreamtable.farm.alert.FarmAlerts;
import com.davisr.thedreamtable.GameSetup;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import java.util.UUID;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FarmAlertsTest {
	private static final FarmKey FARM = new FarmKey(new Identifier("minecraft", "overworld"), UUID.fromString("10000000-0000-0000-0000-000000000001"));

	@BeforeAll
	static void bootstrap() {
		GameSetup.bootstrap();
	}

	@Test
	void keepsOneActiveAlertPerTypeAndUpdatesItsQuantity() {
		FarmAlerts alerts = new FarmAlerts();

		assertEquals(FarmAlerts.Transition.NEW, alerts.observe(FarmAlertType.BLOCKS_MISSING, 3, 100L));
		long event = alerts.active(FarmAlertType.BLOCKS_MISSING).orElseThrow().eventId();
		assertEquals(FarmAlerts.Transition.UNCHANGED, alerts.observe(FarmAlertType.BLOCKS_MISSING, 3, 101L));
		assertEquals(FarmAlerts.Transition.UPDATED, alerts.observe(FarmAlertType.BLOCKS_MISSING, 5, 102L));

		assertEquals(1, alerts.active().size());
		assertEquals(event, alerts.active(FarmAlertType.BLOCKS_MISSING).orElseThrow().eventId());
		assertEquals(5, alerts.active(FarmAlertType.BLOCKS_MISSING).orElseThrow().quantity());
	}

	@Test
	void unknownObservationDoesNotResolveAnActiveProblem() {
		FarmAlerts alerts = new FarmAlerts();
		alerts.observe(FarmAlertType.STORAGE_FULL, 1, 100L);

		assertEquals(FarmAlerts.Transition.UNKNOWN, alerts.unknown(FarmAlertType.STORAGE_FULL));
		assertTrue(alerts.active(FarmAlertType.STORAGE_FULL).isPresent());
	}

	@Test
	void resolvedAndInformationalEventsKeepOnlyTwentyHistoryEntries() {
		FarmAlerts alerts = new FarmAlerts();
		for (int index = 0; index < 22; index++) {
			alerts.observe(FarmAlertType.REPAIR_BLOCKED, index + 1, index);
			assertEquals(FarmAlerts.Transition.RESOLVED, alerts.resolve(FarmAlertType.REPAIR_BLOCKED, index + 100L));
		}

		alerts.record(FarmAlertType.REPAIR_COMPLETED, 122L);

		assertFalse(alerts.active(FarmAlertType.REPAIR_BLOCKED).isPresent());
		assertEquals(20, alerts.history().size());
		assertEquals(FarmAlertType.REPAIR_COMPLETED, alerts.history().get(0).type());
	}

	@Test
	void persistsActiveAndHistoryEventsWithTheirStableReferences() {
		FarmAlerts alerts = new FarmAlerts();
		alerts.observe(FarmAlertType.DESIGN_UNAVAILABLE, 1, 90L);
		alerts.record(FarmAlertType.REPAIR_COMPLETED, 91L);

		FarmAlerts restored = FarmAlerts.read(alerts.write(new NbtCompound()));

		assertEquals(alerts.active(FarmAlertType.DESIGN_UNAVAILABLE).orElseThrow(), restored.active(FarmAlertType.DESIGN_UNAVAILABLE).orElseThrow());
		assertEquals(alerts.history(), restored.history());
	}

	@Test
	void repairResumeResolvesBlockedAndFailedAlertsButKeepsTheirHistory() {
		FarmAreas areas = new FarmAreas();
		FarmSite site = new FarmSite(new BlockPos(0, 64, 0), new BlockPos(10, 64, 10), net.minecraft.util.math.Direction.NORTH);
		areas.recordFinished(UUID.randomUUID(), FarmTemplates.get(FarmOptions.SUGAR_CANE.id()), site, Map.of());
		UUID instanceId = areas.finished().get(0).instanceId();
		areas.observeAlert(instanceId, FarmAlertType.REPAIR_BLOCKED, 1, 1_700_000_000_000L);
		areas.observeAlert(instanceId, FarmAlertType.REPAIR_FAILED, 1, 1_700_000_000_001L);

		areas.resolveRepairAlerts(instanceId, 1_700_000_000_002L);

		FarmAlerts alerts = areas.alerts(instanceId).orElseThrow();
		assertFalse(alerts.active(FarmAlertType.REPAIR_BLOCKED).isPresent());
		assertFalse(alerts.active(FarmAlertType.REPAIR_FAILED).isPresent());
		assertEquals(2, alerts.history().size());
		assertEquals(FarmAlertType.REPAIR_FAILED, alerts.history().get(0).type());
		assertEquals(FarmAlertType.REPAIR_BLOCKED, alerts.history().get(1).type());
	}
}
