package com.davisr.thedreamtable.screen.page;

import com.davisr.thedreamtable.DreamTableConfig;
import com.davisr.thedreamtable.client.ClientSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The server switch wins over the player's own switch, and both default to on. */
class DeliverNearbyGateTest {
	@Test
	void bothSwitchesDefaultToOn() {
		assertTrue(DreamTableConfig.deliverFromNearby());
		assertTrue(ClientSettings.deliverFromNearby());
	}

	@Test
	void buttonIsAvailableOnlyWhenServerAndPlayerAllowIt() {
		assertNull(MaterialsPage.nearbyBlocker(false, true, true));
		assertEquals("gui.the_dream_table.deliver_nearby_off_player", MaterialsPage.nearbyBlocker(false, true, false));
	}

	@Test
	void serverOffWinsWhateverThePlayerChose() {
		assertEquals("gui.the_dream_table.deliver_nearby_off_server", MaterialsPage.nearbyBlocker(false, false, true));
		assertEquals("gui.the_dream_table.deliver_nearby_off_server", MaterialsPage.nearbyBlocker(false, false, false));
	}

	@Test
	void creativeNeverDelivers() {
		assertEquals("gui.the_dream_table.deliver_creative", MaterialsPage.nearbyBlocker(true, true, true));
	}
}
