package com.davisr.thedreamtable.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Players' own preferences file follows them between worlds, so its name must not change. */
class ClientSettingsFileTest {
	@Test
	void clientSettingsFileNameIsFrozen() {
		assertEquals("the_dream_table-client.properties", ClientSettings.FILE);
	}
}
