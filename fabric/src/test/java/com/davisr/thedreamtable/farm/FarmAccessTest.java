package com.davisr.thedreamtable.farm;

import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FarmAccessTest {
	private static final UUID OWNER = UUID.fromString("10000000-0000-0000-0000-000000000001");
	private static final UUID OPERATOR = UUID.fromString("20000000-0000-0000-0000-000000000002");
	private static final UUID VIEWER = UUID.fromString("30000000-0000-0000-0000-000000000003");
	private static final UUID STRANGER = UUID.fromString("40000000-0000-0000-0000-000000000004");
	private static final FarmRecord FARM = new FarmRecord(
			new FarmKey(Identifier.of("minecraft", "overworld"), UUID.fromString("50000000-0000-0000-0000-000000000005")),
			OWNER,
			Map.of(
					OPERATOR, new FarmGrant(OPERATOR, FarmRole.OPERATOR, 10L),
					VIEWER, new FarmGrant(VIEWER, FarmRole.VIEWER, 20L)
			)
	);

	@Test
	void resolvesServerOwnedRolesAndHidesGrantsWhenSharingIsDisabled() {
		assertEquals(FarmRole.OWNER, FarmAccess.role(FARM, OWNER, true).orElseThrow());
		assertEquals(FarmRole.OPERATOR, FarmAccess.role(FARM, OPERATOR, true).orElseThrow());
		assertEquals(FarmRole.VIEWER, FarmAccess.role(FARM, VIEWER, true).orElseThrow());
		assertTrue(FarmAccess.role(FARM, STRANGER, true).isEmpty());
		assertEquals(FarmRole.OWNER, FarmAccess.role(FARM, OWNER, false).orElseThrow());
		assertTrue(FarmAccess.role(FARM, OPERATOR, false).isEmpty());
	}

	@Test
	void ownerCanPerformEveryAction() {
		for (FarmAction action : FarmAction.values()) {
			assertTrue(FarmAccess.allows(FARM, OWNER, action, true), action.name());
		}
	}

	@Test
	void operatorCanOperateButCannotAdministerOrDestroy() {
		for (FarmAction action : new FarmAction[]{FarmAction.VIEW, FarmAction.FAVORITE, FarmAction.TRACK, FarmAction.ALERTS,
				FarmAction.REPAIR, FarmAction.KEEP_LOADED}) {
			assertTrue(FarmAccess.allows(FARM, OPERATOR, action, true), action.name());
		}
		for (FarmAction action : new FarmAction[]{FarmAction.RENAME, FarmAction.SHARE, FarmAction.TAKE_DOWN, FarmAction.FORGET}) {
			assertFalse(FarmAccess.allows(FARM, OPERATOR, action, true), action.name());
		}
	}

	@Test
	void viewerIsReadOnlyAndUnknownActorsHaveNoAccess() {
		for (FarmAction action : new FarmAction[]{FarmAction.VIEW, FarmAction.FAVORITE, FarmAction.TRACK, FarmAction.ALERTS}) {
			assertTrue(FarmAccess.allows(FARM, VIEWER, action, true), action.name());
		}
		for (FarmAction action : new FarmAction[]{FarmAction.REPAIR, FarmAction.KEEP_LOADED, FarmAction.RENAME,
				FarmAction.SHARE, FarmAction.TAKE_DOWN, FarmAction.FORGET}) {
			assertFalse(FarmAccess.allows(FARM, VIEWER, action, true), action.name());
		}
		for (FarmAction action : FarmAction.values()) {
			assertFalse(FarmAccess.allows(FARM, STRANGER, action, true), action.name());
		}
	}
}
