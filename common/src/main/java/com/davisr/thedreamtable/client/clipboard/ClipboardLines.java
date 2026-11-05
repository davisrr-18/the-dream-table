package com.davisr.thedreamtable.client.clipboard;

import com.davisr.thedreamtable.block.entity.TableSession;
import com.davisr.thedreamtable.farm.FarmGrowth;
import com.davisr.thedreamtable.farm.FarmStep;
import com.davisr.thedreamtable.farm.FarmTemplate;
import com.davisr.thedreamtable.farm.MaterialSpec;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;
import java.util.function.UnaryOperator;

/**
 * The two clipboard columns: each material once, and the build order with repeated steps merged. Water is no material and
 * is never delivered: its steps have no line, and a farm with water starts its order with one water line.
 */
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

	public static List<Line> totals(TableSession table, FarmTemplate template) {
		return totals(template, stored(table, template), table::resolve);
	}

	static List<Line> totals(FarmTemplate template, ToIntFunction<Item> stored, UnaryOperator<Item> resolve) {
		List<Line> lines = new ArrayList<>();
		for (MaterialSpec spec : template.materials()) {
			int have = Math.min(stored.applyAsInt(spec.item()), spec.count());
			lines.add(new Line(resolve.apply(spec.item()).getDefaultStack(), have, spec.count()));
		}
		return lines;
	}

	/** Delivered items fill the order from the top, so earlier steps are struck through first. */
	public static List<Line> order(TableSession table, FarmTemplate template) {
		return order(template, stored(table, template), table::resolve);
	}

	/** Delivered counts, except that an infinite water source near the table counts as all the water buckets delivered. */
	private static ToIntFunction<Item> stored(TableSession table, FarmTemplate template) {
		boolean covered = WaterRow.needed(template) && WaterRow.nearby(table, template);
		return item -> covered && item == Items.WATER_BUCKET ? Integer.MAX_VALUE : table.stored(item);
	}

	static List<Line> order(FarmTemplate template, ToIntFunction<Item> stored, UnaryOperator<Item> resolve) {
		Map<Item, Integer> remaining = new HashMap<>();
		for (MaterialSpec spec : template.materials()) {
			remaining.putIfAbsent(spec.item(), stored.applyAsInt(spec.item()));
		}
		List<Line> lines = new ArrayList<>();
		Item current = null;
		int need = 0;
		for (int index = 0; index < template.steps().size(); index++) {
			FarmStep step = template.steps().get(index);
			Item item = step.item();
			if (item == null || FarmGrowth.isGrowth(template, index)) {
				continue;
			}
			if (item == current) {
				need++;
				continue;
			}
			if (current != null) {
				lines.add(line(resolve, current, need, remaining));
			}
			current = item;
			need = 1;
		}
		if (current != null) {
			lines.add(line(resolve, current, need, remaining));
		}
		return lines;
	}

	public static List<Line> page(List<Line> lines, int page) {
		int from = page * ClipboardLayout.ROWS_PER_PAGE;
		return lines.subList(Math.min(from, lines.size()), Math.min(lines.size(), from + ClipboardLayout.ROWS_PER_PAGE));
	}

	private static Line line(UnaryOperator<Item> resolve, Item item, int need, Map<Item, Integer> remaining) {
		int available = remaining.getOrDefault(item, 0);
		int have = Math.min(need, available);
		remaining.put(item, available - have);
		return new Line(resolve.apply(item).getDefaultStack(), have, need);
	}
}
