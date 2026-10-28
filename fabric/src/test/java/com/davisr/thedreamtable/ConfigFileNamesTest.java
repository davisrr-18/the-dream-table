package com.davisr.thedreamtable;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Server settings file; players edit it by hand, so its name must not change. */
class ConfigFileNamesTest {
	@Test
	void serverConfigFileNameIsFrozen() {
		assertEquals("the_dream_table.properties", DreamTableConfig.FILE);
	}
}
