package com.davisr.thedreamtable.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoadedFarmsViewTest {
	@Test
	void requestsEachPayloadOnceUntilTheFarmSelectionChanges() {
		LoadedFarmsView.RequestState state = new LoadedFarmsView.RequestState();

		assertTrue(state.requestFarms());
		assertFalse(state.requestFarms());
		assertTrue(state.select("farm-a"));
		assertTrue(state.requestDetails());
		assertFalse(state.requestDetails());
		assertTrue(state.requestAlerts());
		assertFalse(state.requestAlerts());
		state.invalidateDetails();
		assertTrue(state.requestDetails());
		assertFalse(state.requestDetails());

		assertFalse(state.select("farm-a"));
		assertTrue(state.select("farm-b"));
		assertTrue(state.requestDetails());
		assertTrue(state.requestAlerts());
	}
}
