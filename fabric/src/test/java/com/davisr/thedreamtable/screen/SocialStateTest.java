package com.davisr.thedreamtable.screen;

import com.davisr.thedreamtable.DreamTableConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The server's Show Other Farms switch travels as the synced social menu property. */
class SocialStateTest {
	@Test
	void serverSwitchDefaultsToOn() {
		assertTrue(DreamTableConfig.showOtherFarmsEnabled());
	}

	@Test
	void everythingAllowedIsZeroSoAnUnsyncedClientReadsAsAllowed() {
		assertEquals(0, DreamTableScreenHandler.socialState(true));
	}

	@Test
	void theServerSwitchSetsItsBit() {
		assertNotEquals(0, DreamTableScreenHandler.socialState(false));
	}
}
