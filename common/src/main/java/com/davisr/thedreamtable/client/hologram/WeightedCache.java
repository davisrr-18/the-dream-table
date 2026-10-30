package com.davisr.thedreamtable.client.hologram;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * A least-recently-used cache sized by the total weight of its entries (for the hologram, the number of block parts)
 * instead of their count, so one big farm and many small ones are weighed fairly. An entry used or added during the
 * current frame is never evicted: a farm bigger than the whole capacity then stays usable instead of being thrown out
 * and rebuilt every frame, and the cache simply runs over capacity until the next frame. Not thread-safe; it is only
 * used from the render thread.
 */
public final class WeightedCache<K, V> {
	private final long capacity;
	private final BiConsumer<K, V> onEvict;
	private final LinkedHashMap<K, Slot<V>> entries = new LinkedHashMap<>(16, 0.75F, true);
	private long weight;

	private static final class Slot<V> {
		private final V value;
		private final long weight;
		private long frame;

		private Slot(V value, long weight, long frame) {
			this.value = value;
			this.weight = weight;
			this.frame = frame;
		}
	}

	public WeightedCache(long capacity, BiConsumer<K, V> onEvict) {
		this.capacity = capacity;
		this.onEvict = onEvict;
	}

	/** The value, or null, marking it as used in {@code frame} and as the most recently used. */
	public V get(K key, long frame) {
		Slot<V> slot = entries.get(key);
		if (slot == null) {
			return null;
		}
		slot.frame = frame;
		return slot.value;
	}

	/** Stores the value (replacing and evicting a former one) and then evicts the least recently used entries over capacity. */
	public void put(K key, V value, long entryWeight, long frame) {
		long counted = Math.max(1, entryWeight);
		Slot<V> former = entries.put(key, new Slot<>(value, counted, frame));
		if (former != null) {
			weight -= former.weight;
			onEvict.accept(key, former.value);
		}
		weight += counted;
		trim(frame);
	}

	private void trim(long frame) {
		Iterator<Map.Entry<K, Slot<V>>> oldest = entries.entrySet().iterator();
		while (weight > capacity && oldest.hasNext()) {
			Map.Entry<K, Slot<V>> entry = oldest.next();
			if (entry.getValue().frame >= frame) {
				// Eldest first: everything after this one was used this frame as well.
				break;
			}
			oldest.remove();
			weight -= entry.getValue().weight;
			onEvict.accept(entry.getKey(), entry.getValue().value);
		}
	}

	/** Evicts every entry. */
	public void clear() {
		List<Map.Entry<K, Slot<V>>> all = new ArrayList<>(entries.entrySet());
		entries.clear();
		weight = 0;
		for (Map.Entry<K, Slot<V>> entry : all) {
			onEvict.accept(entry.getKey(), entry.getValue().value);
		}
	}

	public long weight() {
		return weight;
	}

	public int size() {
		return entries.size();
	}

	public boolean contains(K key) {
		return entries.containsKey(key);
	}
}
