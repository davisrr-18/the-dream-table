package com.davisr.thedreamtable.client.clipboard;

import com.davisr.thedreamtable.block.entity.DreamTableBlockEntity;
import com.davisr.thedreamtable.farm.FarmStep;
import com.davisr.thedreamtable.farm.FarmTemplate;
import com.davisr.thedreamtable.farm.MaterialSpec;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The two clipboard columns: each material once, and the build order with repeated steps merged. */
public final class ClipboardLines {
	private ClipboardLines() {
	}

	public record Line(ItemStack stack, int have, int need) {
		public boolean done() {
			return have >= need;
		}

		public String count() {
			return have + "/" + need;
		}
	}

	public static List<Line> totals(DreamTableBlockEntity table, FarmTemplate template) {
		List<Line> lines = new ArrayList<>();
		for (MaterialSpec spec : template.materials()) {
			int have = Math.min(table.stored(spec.item()), spec.count());
			lines.add(new Line(table.resolve(spec.item()).getDefaultStack(), have, spec.count()));
		}
		return lines;
	}

	/** Delivered items fill the order from the top, so earlier steps are struck through first. */
	public static List<Line> order(DreamTableBlockEntity table, FarmTemplate template) {
		Map<Item, Integer> remaining = new HashMap<>();
		for (MaterialSpec spec : template.materials()) {
			remaining.putIfAbsent(spec.item(), table.stored(spec.item()));
		}
		List<Line> lines = new ArrayList<>();
		Item current = null;
		int need = 0;
		for (FarmStep step : template.steps()) {
			Item item = step.item();
			if (item == null) {
				continue;
			}
			if (item == current) {
				need++;
				continue;
			}
			if (current != null) {
				lines.add(line(table, current, need, remaining));
			}
			current = item;
			need = 1;
		}
		if (current != null) {
			lines.add(line(table, current, need, remaining));
		}
		return lines;
	}

	public static List<Line> page(List<Line> lines, int page) {
		int from = page * ClipboardLayout.ROWS_PER_PAGE;
		return lines.subList(Math.min(from, lines.size()), Math.min(lines.size(), from + ClipboardLayout.ROWS_PER_PAGE));
	}

	private static Line line(DreamTableBlockEntity table, Item item, int need, Map<Item, Integer> remaining) {
		int available = remaining.getOrDefault(item, 0);
		int have = Math.min(need, available);
		remaining.put(item, available - have);
		return new Line(table.resolve(item).getDefaultStack(), have, need);
	}
}
