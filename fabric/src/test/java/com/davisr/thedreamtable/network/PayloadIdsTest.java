package com.davisr.thedreamtable.network;

import com.davisr.thedreamtable.GameSetup;
import net.minecraft.network.packet.CustomPayload;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The payload ids are the wire protocol: renaming one breaks every client and server that has the other side's version. */
class PayloadIdsTest {
	@BeforeAll
	static void bootstrap() {
		GameSetup.bootstrap();
	}

	@Test
	void payloadIdsAreFrozen() {
		List<CustomPayload.Id<?>> ids = List.of(
				DepositMaterialPayload.ID,
				PlaceFarmPayload.ID,
				CheckPlacementPayload.ID,
				PlacementResultPayload.ID,
				SessionSyncPayload.ID,
				RequestSessionPayload.ID,
				FarmAreasPayload.ID,
				FarmAreasPayload.WatchAreasPayload.ID,
				FarmActionResultPayload.ID,
				FarmAlertsPayload.ID,
				FarmAlertsPayload.Request.ID,
				FarmAlertAckPayload.ID,
				FarmRenamePayload.ID,
				FarmInvitePayload.ID,
				FarmInviteResponsePayload.ID,
				FarmCollaboratorPayload.ID,
				FarmPreferencePayload.ID,
				FarmCarePayload.ID,
				LoadedFarmsPayload.ID,
				LoadedFarmsPayload.Request.ID,
				ServerSchematicsPayload.ID,
				ServerSchematicsPayload.Request.ID,
				ServerSchematicsPayload.Farm.ID,
				FarmDetailsPayload.ID,
				FarmDetailsPayload.Request.ID,
				TrackedFarmPayload.ID,
				TableNoticePayload.ID);
		Set<String> actual = ids.stream().map(id -> id.id().toString()).collect(Collectors.toCollection(TreeSet::new));

		assertEquals(ids.size(), actual.size(), "payload ids must be unique");
		assertEquals(new TreeSet<>(Set.of(
				"the_dream_table:check_placement",
				"the_dream_table:deposit_material",
				"the_dream_table:farm_action_result",
				"the_dream_table:farm_alert_ack",
				"the_dream_table:farm_alerts",
				"the_dream_table:farm_alerts_request",
				"the_dream_table:farm_areas",
				"the_dream_table:farm_care",
				"the_dream_table:farm_collaborator",
				"the_dream_table:farm_details",
				"the_dream_table:farm_details_request",
				"the_dream_table:farm_invite",
				"the_dream_table:farm_invite_response",
				"the_dream_table:farm_preference",
				"the_dream_table:farm_rename",
				"the_dream_table:loaded_farms",
				"the_dream_table:loaded_farms_request",
				"the_dream_table:place_farm",
				"the_dream_table:placement_result",
				"the_dream_table:request_session",
				"the_dream_table:schematic_farm",
				"the_dream_table:schematic_request",
				"the_dream_table:server_schematics",
				"the_dream_table:session_sync",
				"the_dream_table:table_notice",
				"the_dream_table:tracked_farm",
				"the_dream_table:watch_areas")), actual);
	}
}
