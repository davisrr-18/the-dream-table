package com.davisr.thedreamtable.network;

import com.davisr.thedreamtable.farm.FarmAction;
import com.davisr.thedreamtable.farm.FarmKey;
import com.davisr.thedreamtable.farm.FarmRecord;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FarmPayloadAuthorizationTest {
	private static final UUID OWNER = UUID.fromString("10000000-0000-0000-0000-000000000001");
	private static final UUID STRANGER = UUID.fromString("40000000-0000-0000-0000-000000000004");
	private static final FarmRecord FARM = new FarmRecord(
			new FarmKey(Identifier.of("minecraft", "overworld"), UUID.fromString("50000000-0000-0000-0000-000000000005")),
			OWNER);

	@Test
	void staleAndForeignRequestsAreRejected() {
		assertEquals(FarmActionResultPayload.Result.NOT_FOUND,
				FarmPayloadGuard.authorize(null, OWNER, FarmAction.VIEW));
		assertEquals(FarmActionResultPayload.Result.FORBIDDEN,
				FarmPayloadGuard.authorize(FARM, STRANGER, FarmAction.VIEW));
		assertEquals(FarmActionResultPayload.Result.FORBIDDEN,
				FarmPayloadGuard.authorize(FARM, STRANGER, FarmAction.REPAIR));
	}

	@Test
	void theOwnerIsAuthorizedForEveryPayloadCapability() {
		for (FarmAction action : FarmAction.values()) {
			assertEquals(FarmActionResultPayload.Result.SUCCESS,
					FarmPayloadGuard.authorize(FARM, OWNER, action), action.name());
		}
	}

	@Test
	void malformedPublicKeysAreInvalidInputRatherThanLookups() {
		assertEquals(FarmActionResultPayload.Result.INVALID_INPUT, FarmPayloadGuard.parse("forged").result());
		assertEquals(FarmActionResultPayload.Result.SUCCESS,
				FarmPayloadGuard.parse(FARM.key().externalForm()).result());
	}
}
