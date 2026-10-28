package com.davisr.thedreamtable.network;

import com.davisr.thedreamtable.farm.FarmAction;
import com.davisr.thedreamtable.farm.FarmGrant;
import com.davisr.thedreamtable.farm.FarmKey;
import com.davisr.thedreamtable.farm.FarmRecord;
import com.davisr.thedreamtable.farm.FarmRole;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FarmPayloadAuthorizationTest {
	private static final UUID OWNER = UUID.fromString("10000000-0000-0000-0000-000000000001");
	private static final UUID OPERATOR = UUID.fromString("20000000-0000-0000-0000-000000000002");
	private static final UUID VIEWER = UUID.fromString("30000000-0000-0000-0000-000000000003");
	private static final UUID STRANGER = UUID.fromString("40000000-0000-0000-0000-000000000004");
	private static final FarmRecord FARM = new FarmRecord(
			new FarmKey(Identifier.of("minecraft", "overworld"), UUID.fromString("50000000-0000-0000-0000-000000000005")),
			OWNER,
			Map.of(
					OPERATOR, new FarmGrant(OPERATOR, FarmRole.OPERATOR, 1L),
					VIEWER, new FarmGrant(VIEWER, FarmRole.VIEWER, 1L)));

	@Test
	void staleForeignAndDisabledSharingRequestsAreRejected() {
		assertEquals(FarmActionResultPayload.Result.NOT_FOUND,
				FarmPayloadGuard.authorize(null, OWNER, FarmAction.VIEW, true).result());
		assertEquals(FarmActionResultPayload.Result.FORBIDDEN,
				FarmPayloadGuard.authorize(FARM, STRANGER, FarmAction.VIEW, true).result());
		assertEquals(FarmActionResultPayload.Result.FORBIDDEN,
				FarmPayloadGuard.authorize(FARM, OPERATOR, FarmAction.VIEW, false).result());
	}

	@Test
	void serverOwnedRolesControlEveryPayloadCapability() {
		assertEquals(FarmActionResultPayload.Result.SUCCESS,
				FarmPayloadGuard.authorize(FARM, OWNER, FarmAction.RENAME, true).result());
		assertEquals(FarmActionResultPayload.Result.SUCCESS,
				FarmPayloadGuard.authorize(FARM, OPERATOR, FarmAction.REPAIR, true).result());
		assertEquals(FarmActionResultPayload.Result.FORBIDDEN,
				FarmPayloadGuard.authorize(FARM, OPERATOR, FarmAction.TAKE_DOWN, true).result());
		assertEquals(FarmActionResultPayload.Result.SUCCESS,
				FarmPayloadGuard.authorize(FARM, VIEWER, FarmAction.FAVORITE, true).result());
		assertEquals(FarmActionResultPayload.Result.FORBIDDEN,
				FarmPayloadGuard.authorize(FARM, VIEWER, FarmAction.KEEP_LOADED, true).result());
	}

	@Test
	void malformedPublicKeysAreInvalidInputRatherThanLookups() {
		assertEquals(FarmActionResultPayload.Result.INVALID_INPUT, FarmPayloadGuard.parse("forged").result());
		assertEquals(FarmActionResultPayload.Result.SUCCESS,
				FarmPayloadGuard.parse(FARM.key().externalForm()).result());
	}
}
