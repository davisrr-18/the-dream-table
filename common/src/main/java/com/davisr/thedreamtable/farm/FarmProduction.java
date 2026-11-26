package com.davisr.thedreamtable.farm;

import com.davisr.thedreamtable.Nbts;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What one finished farm's chests and barrels gained over recent hours, by item id. Only increases count, so a player
 * taking items out never lowers it, and anything a player puts in counts as production. A full chest hides output.
 * The totals halve whenever more than {@link #DECAY_TICKS} of time has been observed, so the rate follows recent output.
 * The last snapshot is never saved: after a load, or after the farm was unloaded for a while, the next sample only sets
 * a new baseline.
 */
public final class FarmProduction {
	public static final int SHOWN = 8;
	public static final long TICKS_PER_HOUR = 72000L;
	static final long DECAY_TICKS = 144000L;
	static final int MAX_ITEMS = 32;
	private static final String TICKS = "Ticks";
	private static final String ITEMS = "Items";
	private static final String ID = "Id";
	private static final String COUNT = "Count";

	/** Items gained per hour of observed time, as shown on the farm's page. */
	public record Rate(String item, int perHour) {
	}

	private final Map<String, Long> totals = new HashMap<>();
	private long observedTicks;
	private Map<String, Integer> baseline;
	private long lastSampleTick = -1L;

	/**
	 * Adds each item's gain since the last snapshot and the elapsed time. The first snapshot after creation, a load or
	 * {@link #gap} only sets the baseline. Returns whether anything that is saved changed.
	 */
	public boolean observe(Map<String, Integer> snapshot, long elapsedTicks) {
		Map<String, Integer> before = baseline;
		baseline = Map.copyOf(snapshot);
		if (before == null || elapsedTicks <= 0) {
			return false;
		}
		// Idle time is saved too (once per observed minute), or a restart would leave the rate counted over too short a time.
		boolean changed = (observedTicks + elapsedTicks) / 1200L != observedTicks / 1200L;
		for (Map.Entry<String, Integer> entry : snapshot.entrySet()) {
			int gain = entry.getValue() - before.getOrDefault(entry.getKey(), 0);
			if (gain > 0) {
				totals.merge(entry.getKey(), (long) gain, Long::sum);
				changed = true;
			}
		}
		observedTicks += elapsedTicks;
		while (observedTicks > DECAY_TICKS) {
			observedTicks /= 2;
			totals.replaceAll((item, total) -> total / 2);
			totals.values().removeIf(total -> total <= 0);
			changed = true;
		}
		changed |= bound();
		return changed;
	}

	/** A sample at server tick {@code now}: the time since this farm's previous sample counts, unless there was a {@link #gap}. */
	public boolean sample(Map<String, Integer> snapshot, long now) {
		long elapsed = lastSampleTick < 0 ? 0 : now - lastSampleTick;
		lastSampleTick = now;
		return observe(snapshot, elapsed);
	}

	/** The farm could not be read (unloaded): what it holds when it comes back is a new baseline, not production. */
	public void gap() {
		baseline = null;
		lastSampleTick = -1L;
	}

	private boolean bound() {
		if (totals.size() <= MAX_ITEMS) {
			return false;
		}
		List<Map.Entry<String, Long>> smallestFirst = new ArrayList<>(totals.entrySet());
		smallestFirst.sort(Map.Entry.<String, Long>comparingByValue().thenComparing(Map.Entry.comparingByKey(Comparator.reverseOrder())));
		for (int index = 0; index < smallestFirst.size() - MAX_ITEMS; index++) {
			totals.remove(smallestFirst.get(index).getKey());
		}
		return true;
	}

	public long observedTicks() {
		return observedTicks;
	}

	public int observedMinutes() {
		return (int) Math.min(Integer.MAX_VALUE, observedTicks / 1200L);
	}

	/** The top {@link #SHOWN} items by items per hour, most first, leaving out any that rounds to none. */
	public List<Rate> perHour() {
		if (observedTicks <= 0) {
			return List.of();
		}
		List<Rate> rates = new ArrayList<>();
		for (Map.Entry<String, Long> entry : totals.entrySet()) {
			long perHour = Math.round((double) entry.getValue() * TICKS_PER_HOUR / observedTicks);
			if (perHour > 0) {
				rates.add(new Rate(entry.getKey(), (int) Math.min(Integer.MAX_VALUE, perHour)));
			}
		}
		rates.sort(Comparator.comparingInt(Rate::perHour).reversed().thenComparing(Rate::item));
		return List.copyOf(rates.subList(0, Math.min(SHOWN, rates.size())));
	}

	public boolean isEmpty() {
		return totals.isEmpty() && observedTicks <= 0;
	}

	public NbtCompound write(NbtCompound nbt) {
		nbt.putLong(TICKS, observedTicks);
		NbtList items = new NbtList();
		totals.forEach((item, total) -> {
			NbtCompound saved = new NbtCompound();
			saved.putString(ID, item);
			saved.putLong(COUNT, total);
			items.add(saved);
		});
		nbt.put(ITEMS, items);
		return nbt;
	}

	/** Entries that are unreadable are skipped; bad data never throws. */
	public static FarmProduction read(NbtCompound nbt) {
		FarmProduction production = new FarmProduction();
		production.observedTicks = Math.max(0L, Math.min(DECAY_TICKS, nbt.getLong(TICKS, 0L)));
		NbtList items = Nbts.list(nbt, ITEMS, NbtCompound.class);
		for (int index = 0; index < items.size() && production.totals.size() < MAX_ITEMS; index++) {
			NbtCompound saved = items.getCompoundOrEmpty(index);
			String id = saved.getString(ID, "");
			long count = saved.getLong(COUNT, 0L);
			if (!id.isEmpty() && id.length() <= 256 && count > 0) {
				production.totals.merge(id, count, Long::sum);
			}
		}
		return production;
	}
}
