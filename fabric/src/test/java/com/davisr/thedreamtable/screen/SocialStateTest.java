package com.davisr.thedreamtable.screen;

import com.davisr.thedreamtable.DreamTableConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The server's Show Other Farms switch and the player's Accept Farm Invites choice travel as one synced menu property. */
class SocialStateTest {
	@Test
	void serverSwitchDefaultsToOn() {
		assertTrue(DreamTableConfig.showOtherFarmsEnabled());
	}

	@Test
	void everythingAllowedIsZeroSoAnUnsyncedClientReadsAsAllowed() {
		assertEquals(0, DreamTableScreenHandler.socialState(true, true));
	}

	@Test
	void eachChoiceUsesItsOwnBit() {
		int serverOff = DreamTableScreenHandler.socialState(false, true);
		int invitesOff = DreamTableScreenHandler.socialState(true, false);
		assertNotEquals(0, serverOff);
		assertNotEquals(0, invitesOff);
		assertNotEquals(serverOff, invitesOff);
		assertEquals(serverOff | invitesOff, DreamTableScreenHandler.socialState(false, false));
	}
}
