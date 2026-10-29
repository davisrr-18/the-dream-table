package com.davisr.thedreamtable;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The true or false settings: a value nobody meant as false must not silently switch an option off. */
class ConfigFlagTest {
	@Test
	void trueAndFalseAreReadInAnyCaseWithSpaces() {
		assertTrue(DreamTableConfig.flag("k", " TRUE ", false));
		assertFalse(DreamTableConfig.flag("k", "False", true));
	}

	@Test
	void aMissingValueIsTheDefault() {
		assertTrue(DreamTableConfig.flag("k", null, true));
		assertFalse(DreamTableConfig.flag("k", null, false));
	}

	@Test
	void anUnrecognizedValueIsTheDefaultNotFalse() {
		assertTrue(DreamTableConfig.flag("farm_sharing_enabled", "yes", true));
		assertTrue(DreamTableConfig.flag("farm_sharing_enabled", "", true));
		assertFalse(DreamTableConfig.flag("load_farms_while_offline", "1", false));
	}
}
