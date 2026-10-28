package com.davisr.thedreamtable.network;

import com.davisr.thedreamtable.farm.FarmGrant;
import com.davisr.thedreamtable.farm.FarmRole;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FarmDetailsPayloadTest {
	private static final UUID VIEWER = UUID.fromString("10000000-0000-0000-0000-000000000001");
	private static final UUID OPERATOR = UUID.fromString("20000000-0000-0000-0000-000000000002");

	@Test
	void onlyOwnersReceiveBoundedCollaboratorIdentities() {
		Map<UUID, FarmGrant> grants = new LinkedHashMap<>();
		grants.put(VIEWER, new FarmGrant(VIEWER, FarmRole.VIEWER, 1L));
		grants.put(OPERATOR, new FarmGrant(OPERATOR, FarmRole.OPERATOR, 2L));

		assertEquals(List.of(), FarmDetailsPayload.collaborators(grants, FarmRole.VIEWER, UUID::toString));
		assertEquals(List.of(), FarmDetailsPayload.collaborators(grants, FarmRole.OPERATOR, UUID::toString));
		assertEquals(List.of(
				new FarmDetailsPayload.CollaboratorEntry(VIEWER, VIEWER.toString(), FarmRole.VIEWER),
				new FarmDetailsPayload.CollaboratorEntry(OPERATOR, OPERATOR.toString(), FarmRole.OPERATOR)),
				FarmDetailsPayload.collaborators(grants, FarmRole.OWNER, UUID::toString));
	}
}
