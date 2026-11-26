package com.davisr.thedreamtable.farm;

import com.davisr.thedreamtable.GameSetup;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A finished farm's production is saved with its record, survives a reload, and is dropped with the farm. */
class FarmAreasProductionTest {
	private static final UUID OWNER = UUID.fromString("10000000-0000-0000-0000-000000000001");
	private static final UUID INSTANCE = UUID.fromString("50000000-0000-0000-0000-000000000005");
	private static final long HOUR = FarmProduction.TICKS_PER_HOUR;

	@BeforeAll
	static void setUp() {
		GameSetup.bootstrap();
	}

	private static FarmAreas areaWithFarm() {
		FarmAreas areas = new FarmAreas();
		FarmSite site = new FarmSite(new BlockPos(0, 64, 0), new BlockPos(10, 64, 10), Direction.NORTH);
		areas.recordFinished(OWNER, FarmTemplates.get(FarmOptions.SUGAR_CANE.id()), site, Map.of(), INSTANCE, "My Cane");
		return areas;
	}

	@Test
	void productionSurvivesASaveAndReload() {
		FarmAreas areas = areaWithFarm();
		areas.sampleProduction(INSTANCE, Map.of("minecraft:sugar_cane", 0), 200);
		areas.sampleProduction(INSTANCE, Map.of("minecraft:sugar_cane", 90), 200 + HOUR);

		FarmAreas restored = FarmAreas.read(areas.writeNbt(new NbtCompound(), null), null);

		FarmProduction production = restored.production(INSTANCE).orElseThrow();
		assertEquals(areas.production(INSTANCE).orElseThrow().perHour(), production.perHour());
		assertEquals(90, production.perHour().getFirst().perHour());
		assertEquals(60, production.observedMinutes());
	}

	@Test
	void aFarmSavedByOnePointFiveWithoutProductionLoadsUnchanged() {
		FarmAreas areas = areaWithFarm();
		NbtCompound saved = areas.writeNbt(new NbtCompound(), null);
		NbtCompound item = saved.getList("Areas", NbtElement.COMPOUND_TYPE).getCompound(0);
		assertFalse(item.contains("Production"), "a farm nobody sampled writes no tag");

		FarmAreas restored = FarmAreas.read(saved, null);

		assertEquals(1, restored.finishedBy(OWNER).size());
		assertEquals(0, restored.unreadableCount());
		assertTrue(restored.production(INSTANCE).isEmpty());
	}

	@Test
	void takingTheFarmDownDropsItsProduction() {
		FarmAreas areas = areaWithFarm();
		areas.sampleProduction(INSTANCE, Map.of("minecraft:sugar_cane", 0), 200);
		areas.sampleProduction(INSTANCE, Map.of("minecraft:sugar_cane", 9), 400);
		FarmAreas.Finished farm = areas.finishedBy(OWNER).getFirst();

		areas.release(farm.key());

		assertTrue(areas.production(INSTANCE).isEmpty());
		areas.sampleProduction(INSTANCE, Map.of("minecraft:sugar_cane", 9), 600);
		assertTrue(areas.production(INSTANCE).isEmpty(), "a released farm is not sampled back into existence");
	}

	@Test
	void anUnreadableProductionTagDoesNotLoseTheFarm() {
		FarmAreas areas = areaWithFarm();
		areas.sampleProduction(INSTANCE, Map.of("minecraft:sugar_cane", 0), 200);
		areas.sampleProduction(INSTANCE, Map.of("minecraft:sugar_cane", 9), 400);
		NbtCompound saved = areas.writeNbt(new NbtCompound(), null);
		NbtCompound item = saved.getList("Areas", NbtElement.COMPOUND_TYPE).getCompound(0);
		item.putString("Production", "garbage");

		FarmAreas restored = FarmAreas.read(saved, null);

		assertEquals(1, restored.finishedBy(OWNER).size());
		assertTrue(restored.production(INSTANCE).isEmpty());
	}
}
