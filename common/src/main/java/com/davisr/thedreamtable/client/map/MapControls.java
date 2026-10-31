package com.davisr.thedreamtable.client.map;

import java.util.ArrayList;
import java.util.List;

/**
 * Lays out the map's controls panel: items of given widths flow into centred rows no wider than the space, and an item is
 * never split across rows. Pure, so it can be tested without a screen.
 */
public final class MapControls {
	private MapControls() {
	}

	/** The items of each row, in order, by index. An item wider than the space gets a row of its own. */
	public static List<List<Integer>> rows(int[] widths, int maxWidth, int gap) {
		List<List<Integer>> rows = new ArrayList<>();
		List<Integer> row = new ArrayList<>();
		int used = 0;
		for (int i = 0; i < widths.length; i++) {
			int needed = row.isEmpty() ? widths[i] : used + gap + widths[i];
			if (!row.isEmpty() && needed > maxWidth) {
				rows.add(List.copyOf(row));
				row.clear();
				needed = widths[i];
			}
			row.add(i);
			used = needed;
		}
		if (!row.isEmpty()) {
			rows.add(List.copyOf(row));
		}
		return List.copyOf(rows);
	}

	/** The width of a row: its items and the gaps between them. */
	public static int rowWidth(List<Integer> row, int[] widths, int gap) {
		int width = 0;
		for (int index : row) {
			width += widths[index];
		}
		return width + gap * Math.max(0, row.size() - 1);
	}
}
