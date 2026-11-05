package com.davisr.thedreamtable.farm;

import com.davisr.thedreamtable.GameSetup;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Water steps: no bucket is a material, the step keeps its item for saved builds, and the builder asks for a nearby source
 * instead of spending anything. The world-bound checks in {@link FarmBuilder#check} need a server, so these cover the
 * decisions they are made from.
 */
class WaterStepTest {
	// Block states need the registries, so they are made once they are bootstrapped, not at class load.
	private static FarmStep WATER;
	private static FarmStep STONE;
	private static FarmStep FREE;

	@BeforeAll
	static void setUp() {
		GameSetup.bootstrap();
		WATER = FarmStep.block(2, 0, 0, Blocks.WATER.getDefaultState(), Items.WATER_BUCKET);
		STONE = FarmStep.block(0, 0, 0, Blocks.STONE.getDefaultState(), Items.STONE);
		FREE = FarmStep.block(1, 0, 0, Blocks.ANDESITE.getDefaultState(), null);
	}

	private static FarmTemplate farm(FarmStep... steps) {
		List<FarmStep> list = new ArrayList<>(List.of(steps));
		return FarmTemplate.of("water_test", list);
	}

	@Test
	void waterIsNoMaterialOfTheFarm() {
		FarmTemplate farm = farm(STONE, WATER);
		assertEquals(1, farm.materials().size());
		assertEquals(Items.STONE, farm.materials().getFirst().item());
	}

	@Test
	void aWaterOnlyFarmHasNoMaterialsAtAll() {
		assertTrue(farm(WATER).materials().isEmpty());
	}

	@Test
	void theStepKeepsItsBucketItemSoSavedBuildsStillMatch() {
		FarmTemplate farm = farm(STONE, WATER);
		assertEquals(Items.WATER_BUCKET, farm.steps().get(1).item());
		assertTrue(FarmBuilder.isWater(farm.steps().get(1)));
		assertFalse(FarmBuilder.isWater(farm.steps().get(0)));
		assertFalse(FarmBuilder.isWater(FREE));
	}

	@Test
	void aWaterStepSpendsNothingAndAsksForASourceInSurvival() {
		assertEquals(FarmBuilder.Supply.SOURCE, FarmBuilder.supply(WATER, false));
	}

	@Test
	void creativeNeedsNoSourceForWater() {
		assertEquals(FarmBuilder.Supply.FREE, FarmBuilder.supply(WATER, true));
	}

	@Test
	void otherItemsStillComeFromTheDeliveredCounts() {
		assertEquals(FarmBuilder.Supply.COUNTS, FarmBuilder.supply(STONE, false));
		assertEquals(FarmBuilder.Supply.FREE, FarmBuilder.supply(FREE, false));
	}

	@Test
	void anOldWaterRecordStillRefundsNothingOnTakeDown() {
		FarmTemplate farm = farm(STONE, WATER);
		assertFalse(FarmSpawns.refundsOnTakeDown(farm.steps().get(1).item(), true));
		assertFalse(FarmSpawns.refundsOnTakeDown(farm.steps().get(1).item(), false));
	}
}
