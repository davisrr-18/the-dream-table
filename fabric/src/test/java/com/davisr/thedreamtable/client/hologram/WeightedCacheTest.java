package com.davisr.thedreamtable.client.hologram;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeightedCacheTest {
	private final List<String> evicted = new ArrayList<>();

	private WeightedCache<String, String> cache(long capacity) {
		return new WeightedCache<>(capacity, (key, value) -> evicted.add(key));
	}

	@Test
	void sizesByTotalWeightNotByEntryCount() {
		WeightedCache<String, String> cache = cache(100);
		for (int index = 0; index < 20; index++) {
			cache.put("small" + index, "v", 2, index);
		}
		assertEquals(20, cache.size(), "twenty small farms fit, which an 8 entry cache could not hold");
		assertEquals(40, cache.weight());
		assertEquals(List.of(), evicted);

		cache.put("big", "v", 90, 100);
		assertTrue(cache.contains("big"));
		assertTrue(cache.weight() <= 100);
		assertFalse(evicted.isEmpty());
	}


	@Test
	void evictsTheLeastRecentlyUsedFirst() {
		WeightedCache<String, String> cache = cache(10);
		cache.put("a", "v", 4, 1);
		cache.put("b", "v", 4, 2);
		cache.get("a", 3);
		cache.put("c", "v", 4, 4);

		assertEquals(List.of("b"), evicted, "a was used after b");
		assertTrue(cache.contains("a"));
		assertTrue(cache.contains("c"));
		assertEquals(8, cache.weight());
	}

	@Test
	void entriesUsedThisFrameAreNeverEvictedEvenOverCapacity() {
		WeightedCache<String, String> cache = cache(10);
		cache.put("a", "v", 8, 5);
		cache.put("b", "v", 8, 5);

		assertEquals(List.of(), evicted, "both were added in the current frame");
		assertEquals(16, cache.weight());

		cache.put("c", "v", 1, 6);
		assertEquals(List.of("a"), evicted, "the next frame trims the eldest entry, which brings the weight back under capacity");
		assertEquals(9, cache.weight());
		assertTrue(cache.contains("b"));
		assertTrue(cache.contains("c"));
	}

	@Test
	void aFarmBiggerThanTheWholeCacheStaysUsable() {
		WeightedCache<String, String> cache = cache(5);
		cache.put("huge", "v", 500, 1);
		assertEquals("v", cache.get("huge", 1));
		assertEquals("v", cache.get("huge", 2));
		assertEquals(List.of(), evicted);
	}

	@Test
	void replacingAnEntryReleasesTheOldOne() {
		WeightedCache<String, String> cache = cache(100);
		cache.put("a", "old", 10, 1);
		cache.put("a", "new", 20, 2);

		assertEquals(List.of("a"), evicted);
		assertEquals(20, cache.weight());
		assertEquals("new", cache.get("a", 2));
	}

	@Test
	void aMissIsNullAndClearEvictsEverything() {
		WeightedCache<String, String> cache = cache(100);
		assertNull(cache.get("nothing", 1));
		cache.put("a", "v", 3, 1);
		cache.put("b", "v", 3, 1);
		cache.clear();

		assertEquals(0, cache.size());
		assertEquals(0, cache.weight());
		assertEquals(2, evicted.size());
	}

	@Test
	void everyEntryWeighsAtLeastOne() {
		WeightedCache<String, String> cache = cache(100);
		cache.put("empty", "v", 0, 1);
		assertEquals(1, cache.weight());
	}
}
