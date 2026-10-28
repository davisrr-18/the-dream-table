package com.davisr.thedreamtable.farm;

import com.davisr.thedreamtable.GameSetup;
import com.davisr.thedreamtable.network.FarmActionResultPayload;
import com.davisr.thedreamtable.network.FarmPayloadGuard;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A collaborator whose grant is removed loses every capability on the next authorization, which reads the stored record. */
class FarmRevokedAccessTest {
	private static final UUID OWNER = UUID.fromString("10000000-0000-0000-0000-000000000001");
	private static final UUID OPERATOR = UUID.fromString("20000000-0000-0000-0000-000000000002");
	private static final FarmAction[] ACTIONS = {FarmAction.REPAIR, FarmAction.KEEP_LOADED, FarmAction.VIEW};

	@BeforeAll
	static void setUp() {
		GameSetup.bootstrap();
	}

	private static FarmRecord record(FarmAreas areas) {
		FarmAreas.Finished finished = areas.finishedBy(OWNER).getFirst();
		return FarmDirectory.record(new FarmKey(Identifier.of("minecraft", "overworld"), finished.instanceId()), finished);
	}

	@Test
	void removedOperatorIsForbiddenFromRepairKeepLoadedAndView() {
		FarmAreas areas = new FarmAreas();
		FarmSite site = new FarmSite(new BlockPos(0, 64, 0), new BlockPos(10, 64, 10), Direction.NORTH);
		areas.recordFinished(OWNER, FarmTemplates.get(FarmOptions.SUGAR_CANE.id()), site, Map.of());
		UUID instance = areas.finishedBy(OWNER).getFirst().instanceId();
		areas.putGrant(instance, new FarmGrant(OPERATOR, FarmRole.OPERATOR, 1L));
		for (FarmAction action : ACTIONS) {
			assertEquals(FarmActionResultPayload.Result.SUCCESS, FarmPayloadGuard.authorize(record(areas), OPERATOR, action, true).result());
		}

		assertTrue(areas.removeGrant(instance, OPERATOR));
		assertFalse(areas.removeGrant(instance, OPERATOR));

		FarmRecord revoked = record(areas);
		for (FarmAction action : ACTIONS) {
			FarmPayloadGuard.Authorization result = FarmPayloadGuard.authorize(revoked, OPERATOR, action, true);
			assertEquals(FarmActionResultPayload.Result.FORBIDDEN, result.result(), action.name());
			assertNull(result.role());
		}
		assertEquals(FarmActionResultPayload.Result.SUCCESS, FarmPayloadGuard.authorize(revoked, OWNER, FarmAction.REPAIR, true).result());
	}
}
