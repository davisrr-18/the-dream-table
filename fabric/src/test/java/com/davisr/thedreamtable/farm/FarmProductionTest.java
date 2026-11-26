package com.davisr.thedreamtable.farm;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FarmProductionTest {
	private static final long HOUR = FarmProduction.TICKS_PER_HOUR;

	@Test
	void theFirstSnapshotOnlySetsTheBaseline() {
		FarmProduction production = new FarmProduction();

		assertFalse(production.observe(Map.of("minecraft:wheat", 500), 200));

		assertEquals(0, production.observedTicks());
		assertTrue(production.perHour().isEmpty());
	}

	@Test
	void onlyIncreasesCountAndTakingItemsOutIsIgnored() {
		FarmProduction production = new FarmProduction();
		production.observe(Map.of("minecraft:wheat", 100), HOUR);
		production.observe(Map.of("minecraft:wheat", 40), HOUR / 2);
		production.observe(Map.of("minecraft:wheat", 100), HOUR / 2);

		// 40 -> 100 is the only gain: 60 items over one hour of observed time.
		assertEquals(List.of(new FarmProduction.Rate("minecraft:wheat", 60)), production.perHour());
		assertEquals(HOUR, production.observedTicks());
	}

	@Test
	void anItemThatAppearsCountsFromZero() {
		FarmProduction production = new FarmProduction();
		production.observe(Map.of(), HOUR);
		production.observe(Map.of("minecraft:carrot", 30), HOUR);

		assertEquals(List.of(new FarmProduction.Rate("minecraft:carrot", 30)), production.perHour());
	}

	@Test
	void aGapStartsOverWithoutCountingTheTimeAway() {
		FarmProduction production = new FarmProduction();
		production.observe(Map.of("minecraft:wheat", 10), HOUR);
		production.observe(Map.of("minecraft:wheat", 20), HOUR / 2);
		production.gap();
		// Back from being unloaded holding far more: that is a new baseline, not production.
		assertFalse(production.observe(Map.of("minecraft:wheat", 5000), 99999));

		assertEquals(HOUR / 2, production.observedTicks());
		assertEquals(20, production.perHour().getFirst().perHour());
	}

	@Test
	void sampleCountsTheTicksSinceTheLastSampleAndAGapForgetsThem() {
		FarmProduction production = new FarmProduction();
		production.sample(Map.of("minecraft:wheat", 0), 200);
		production.sample(Map.of("minecraft:wheat", 10), 400);
		assertEquals(200, production.observedTicks());

		production.gap();
		production.sample(Map.of("minecraft:wheat", 10), 90000);
		production.sample(Map.of("minecraft:wheat", 12), 90200);

		assertEquals(400, production.observedTicks());
	}

	@Test
	void observedTimeAboveTwoHoursHalvesEveryTotalAndTheTime() {
		FarmProduction production = new FarmProduction();
		production.observe(Map.of("minecraft:wheat", 0), 1);
		production.observe(Map.of("minecraft:wheat", 200, "minecraft:carrot", 100), FarmProduction.DECAY_TICKS);
		assertEquals(FarmProduction.DECAY_TICKS, production.observedTicks());

		assertTrue(production.observe(Map.of("minecraft:wheat", 200, "minecraft:carrot", 100), 2400));

		assertEquals((FarmProduction.DECAY_TICKS + 2400) / 2, production.observedTicks());
		Map<String, Integer> rates = new HashMap<>();
		production.perHour().forEach(rate -> rates.put(rate.item(), rate.perHour()));
		// 100 and 50 left over 73200 ticks: the rate is the same as before the halving, within rounding.
		assertEquals((int) Math.round(100.0 * HOUR / 73200), rates.get("minecraft:wheat"));
		assertEquals((int) Math.round(50.0 * HOUR / 73200), rates.get("minecraft:carrot"));
	}

	@Test
	void theItemMapIsBoundedAndTheSmallestAreDropped() {
		FarmProduction production = new FarmProduction();
		production.observe(Map.of(), 1);
		Map<String, Integer> snapshot = new HashMap<>();
		for (int index = 1; index <= 40; index++) {
			snapshot.put("test:item_" + index, index * 10);
		}
		production.observe(snapshot, HOUR);

		NbtCompound saved = production.write(new NbtCompound());
		NbtList items = saved.getListOrEmpty("Items");
		assertEquals(FarmProduction.MAX_ITEMS, items.size());
		for (int index = 0; index < items.size(); index++) {
			assertTrue(items.getCompoundOrEmpty(index).getLong("Count", 0L) >= 90, "the eight smallest were dropped");
		}
	}

	@Test
	void perHourListsTheTopEightMostFirstWithTiesByName() {
		FarmProduction production = new FarmProduction();
		production.observe(Map.of(), 1);
		Map<String, Integer> snapshot = new HashMap<>();
		for (int index = 1; index <= 10; index++) {
			snapshot.put("test:item_" + (char) ('a' + index), index * 5);
		}
		snapshot.put("test:item_tie_b", 50);
		snapshot.put("test:item_tie_a", 50);
		production.observe(snapshot, HOUR);

		List<FarmProduction.Rate> rates = production.perHour();

		assertEquals(FarmProduction.SHOWN, rates.size());
		assertEquals(new FarmProduction.Rate("test:item_k", 50), rates.get(0));
		assertEquals(new FarmProduction.Rate("test:item_tie_a", 50), rates.get(1));
		assertEquals(new FarmProduction.Rate("test:item_tie_b", 50), rates.get(2));
		assertTrue(rates.get(7).perHour() <= rates.get(6).perHour());
	}

	@Test
	void aTotalThatHalvesToNothingIsDropped() {
		FarmProduction production = new FarmProduction();
		production.observe(Map.of(), 1);
		production.observe(Map.of("minecraft:wheat", 1), HOUR);
		production.observe(Map.of("minecraft:wheat", 1), FarmProduction.DECAY_TICKS);

		assertTrue(production.perHour().isEmpty());
	}

	@Test
	void savedProductionReadsBackAndTheBaselineIsNotSaved() {
		FarmProduction production = new FarmProduction();
		production.observe(Map.of("minecraft:wheat", 5), HOUR);
		production.observe(Map.of("minecraft:wheat", 65, "minecraft:carrot", 9), HOUR);
		FarmProduction restored = FarmProduction.read(production.write(new NbtCompound()));

		assertEquals(production.perHour(), restored.perHour());
		assertEquals(production.observedTicks(), restored.observedTicks());
		// After a load the first sample re-baselines: a full chest is not production.
		assertFalse(restored.observe(Map.of("minecraft:wheat", 99999), HOUR));
		assertEquals(production.observedTicks(), restored.observedTicks());
	}

	@Test
	void garbageInTheSavedTagIsSkippedWithoutThrowing() {
		NbtCompound saved = new NbtCompound();
		saved.putLong("Ticks", -5);
		NbtList items = new NbtList();
		NbtCompound good = new NbtCompound();
		good.putString("Id", "minecraft:wheat");
		good.putLong("Count", 7);
		items.add(good);
		items.add(new NbtCompound());
		NbtCompound negative = new NbtCompound();
		negative.putString("Id", "minecraft:carrot");
		negative.putLong("Count", -3);
		items.add(negative);
		saved.put("Items", items);

		FarmProduction read = FarmProduction.read(saved);
		assertEquals(0, read.observedTicks());
		NbtList kept = read.write(new NbtCompound()).getListOrEmpty("Items");
		assertEquals(1, kept.size());

		NbtCompound wrongTypes = new NbtCompound();
		wrongTypes.putString("Ticks", "soon");
		wrongTypes.putInt("Items", 4);
		assertTrue(FarmProduction.read(wrongTypes).isEmpty());
		assertTrue(FarmProduction.read(new NbtCompound()).isEmpty());
	}

	@Test
	void idleTimeIsReportedForSavingOncePerObservedMinute() {
		FarmProduction production = new FarmProduction();
		production.observe(Map.of("minecraft:wheat", 10), 1);

		assertTrue(production.observe(Map.of("minecraft:wheat", 10), 1200));
		assertFalse(production.observe(Map.of("minecraft:wheat", 10), 200));
		assertEquals(1400, production.observedTicks());
	}
}
