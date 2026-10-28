package com.davisr.thedreamtable.network;

/** Pure routing of one My Farms list request, so a toggle can neither skip its budget nor run the list scan for free. */
final class LoadedFarmsGate {
	private LoadedFarmsGate() {
	}

	enum Route {
		/** A plain list read within its budget: reply with the list. */
		LIST,
		/** A toggle within its budget: authorize it, then queue a coalesced refresh. */
		TOGGLE,
		/** A toggle over its budget: reply busy, no scan. */
		BUSY,
		/** A plain list read over its budget: ignore it. */
		DROP
	}

	/** {@code withinBudget} is the answer of the toggle budget for a toggle and of the list budget otherwise. */
	static Route route(boolean hasToggle, boolean withinBudget) {
		if (hasToggle) {
			return withinBudget ? Route.TOGGLE : Route.BUSY;
		}
		return withinBudget ? Route.LIST : Route.DROP;
	}
}
