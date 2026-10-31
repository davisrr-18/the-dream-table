package com.davisr.thedreamtable.farm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** These names are the files inside saved worlds' data folders; changing one orphans every player's saved farms. */
class PersistentStateKeysTest {
	@Test
	void saveKeysAreFrozen() {
		assertEquals("the_dream_table_farm_preferences", FarmPreferences.STATE_KEY);
		assertEquals("the_dream_table_loaded_farms", FarmLoader.STATE_KEY);
		assertEquals("the_dream_table_farm_areas", FarmAreas.STATE_KEY);
		assertEquals("the_dream_table_pending_refunds", PendingRefunds.STATE_KEY);
		assertEquals("the_dream_table_parked_projects", ParkedProjects.STATE_KEY);
	}
}
