package com.davisr.thedreamtable.client;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Players' own preferences file follows them between worlds, so its name must not change. */
class ClientSettingsFileTest {
	@Test
	void clientSettingsFileNameIsFrozen() {
		assertEquals("the_dream_table-client.properties", ClientSettings.FILE);
	}

	@Test
	void feedbackSwitchKeyIsFrozen() {
		assertEquals("feedback_enabled", ClientSettings.FEEDBACK);
	}

	@Test
	void feedbackIsOnUntilTheFileTurnsItOff() {
		assertTrue(ClientSettings.readFeedbackEnabled(new Properties()));
		Properties off = new Properties();
		off.setProperty(ClientSettings.FEEDBACK, "false");
		assertFalse(ClientSettings.readFeedbackEnabled(off));
	}
}
