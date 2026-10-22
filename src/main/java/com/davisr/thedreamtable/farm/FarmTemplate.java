package com.davisr.thedreamtable.farm;

import net.minecraft.item.Item;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record FarmTemplate(String id, List<FarmStep> steps, List<MaterialSpec> materials) {
	public static final int MAX_SLOTS = 27;

	public static FarmTemplate of(String id, List<FarmStep> steps) {
		Map<Item, Integer> totals = new LinkedHashMap<>();
		for (FarmStep step : steps) {
			if (step.item() != null) {
				totals.merge(step.item(), 1, Integer::sum);
			}
		}
		List<MaterialSpec> materials = new ArrayList<>();
		for (Map.Entry<Item, Integer> entry : totals.entrySet()) {
			int remaining = entry.getValue();
			int stackLimit = Math.max(1, entry.getKey().getMaxCount());
			while (remaining > 0) {
				int stack = Math.min(stackLimit, remaining);
				materials.add(new MaterialSpec(entry.getKey(), stack));
				remaining -= stack;
			}
		}
		if (materials.size() > MAX_SLOTS) {
			throw new IllegalStateException(id + " needs " + materials.size() + " material slots");
		}
		Set<Long> occupied = new HashSet<>();
		List<String> overlaps = new ArrayList<>();
		for (FarmStep step : steps) {
			if (step.isEntity()) {
				continue;
			}
			long key = BlockPos.asLong(step.x(), step.y(), step.z());
			if (!occupied.add(key)) {
				overlaps.add(step.x() + ", " + step.y() + ", " + step.z());
			}
		}
		if (!overlaps.isEmpty()) {
			throw new IllegalStateException(id + " places two blocks at " + String.join("; ", overlaps));
		}
		return new FarmTemplate(id, List.copyOf(steps), List.copyOf(materials));
	}
}
