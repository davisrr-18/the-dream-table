package com.davisr.thedreamtable.farm;

import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FarmAccessTest {
	private static final UUID OWNER = UUID.fromString("10000000-0000-0000-0000-000000000001");
	private static final UUID STRANGER = UUID.fromString("40000000-0000-0000-0000-000000000004");
	private static final FarmRecord FARM = new FarmRecord(
			new FarmKey(Identifier.of("minecraft", "overworld"), UUID.fromString("50000000-0000-0000-0000-000000000005")),
			OWNER);

	@Test
	void onlyTheFarmsOwnerIsItsOwner() {
		assertTrue(FarmAccess.isOwner(FARM, OWNER));
		assertFalse(FarmAccess.isOwner(FARM, STRANGER));
	}

	@Test
	void ownerCanPerformEveryAction() {
		for (FarmAction action : FarmAction.values()) {
			assertTrue(FarmAccess.allows(FARM, OWNER, action), action.name());
		}
	}

	@Test
	void everyOtherPlayerIsForbiddenEveryAction() {
		for (FarmAction action : FarmAction.values()) {
			assertFalse(FarmAccess.allows(FARM, STRANGER, action), action.name());
		}
	}
}
