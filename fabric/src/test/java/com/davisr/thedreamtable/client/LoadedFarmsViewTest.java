package com.davisr.thedreamtable.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoadedFarmsViewTest {
	@Test
	void requestsEachPayloadOnceUntilTheFarmSelectionChanges() {
		LoadedFarmsView.RequestState state = new LoadedFarmsView.RequestState();

		assertTrue(state.requestFarms(0L, false));
		assertFalse(state.requestFarms(10L, true));
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

	@Test
	void resendsTheListRequestUntilItIsAnswered() {
		LoadedFarmsView.RequestState state = new LoadedFarmsView.RequestState();

		assertTrue(state.requestFarms(0L, false));
		// A request the server dropped (for example after a resize) is sent again once the retry delay passed.
		assertFalse(state.requestFarms(LoadedFarmsView.RequestState.RETRY_MS - 1, false));
		assertTrue(state.requestFarms(LoadedFarmsView.RequestState.RETRY_MS, false));
		assertFalse(state.requestFarms(LoadedFarmsView.RequestState.RETRY_MS + 1, false));
		// Once answered it stays quiet, until the page is opened again.
		assertFalse(state.requestFarms(10 * LoadedFarmsView.RequestState.RETRY_MS, true));
		state.reset();
		assertTrue(state.requestFarms(10 * LoadedFarmsView.RequestState.RETRY_MS, false));
	}

	@Test
	void resetForgetsEverythingTheServerSent() {
		LoadedFarmsView.toggleFavorites();
		LoadedFarmsView.select("minecraft:overworld|key");
		LoadedFarmsView.reset();

		assertFalse(LoadedFarmsView.received());
		assertTrue(LoadedFarmsView.farms().isEmpty());
		assertTrue(LoadedFarmsView.invites().isEmpty());
		assertTrue(LoadedFarmsView.dimension().isEmpty());
		assertTrue(LoadedFarmsView.limit() == 0);
		assertTrue(LoadedFarmsView.details() == null);
		LoadedFarmsView.toggleFavorites();
	}

	@Test
	void favoriteFilterHasIndependentClientState() {
		assertFalse(LoadedFarmsView.favoritesOnly());
		LoadedFarmsView.toggleFavorites();
		assertTrue(LoadedFarmsView.favoritesOnly());
		LoadedFarmsView.toggleFavorites();
		assertFalse(LoadedFarmsView.favoritesOnly());
	}
}
