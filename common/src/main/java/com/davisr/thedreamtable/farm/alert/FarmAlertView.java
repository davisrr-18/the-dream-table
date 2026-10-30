package com.davisr.thedreamtable.farm.alert;

import java.util.ArrayList;
import java.util.List;

/** Deterministic active-first paging over one farm's alerts. */
public final class FarmAlertView {
	public record Entry(FarmAlerts.Event event, boolean active) {
	}

	private static final int MAX_PAGE_SIZE = 50;

	private FarmAlertView() {
	}

	public static List<Entry> page(FarmAlerts alerts, int offset, int limit) {
		List<Entry> visible = new ArrayList<>();
		for (FarmAlerts.Event event : alerts.active()) {
			visible.add(new Entry(event, true));
		}
		for (FarmAlerts.Event event : alerts.history()) {
			visible.add(new Entry(event, false));
		}
		int from = Math.min(Math.max(0, offset), visible.size());
		int to = Math.min(visible.size(), from + Math.min(MAX_PAGE_SIZE, Math.max(0, limit)));
		return List.copyOf(visible.subList(from, to));
	}
}
