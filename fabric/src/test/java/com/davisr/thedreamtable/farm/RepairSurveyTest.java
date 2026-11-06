package com.davisr.thedreamtable.farm;

import com.davisr.thedreamtable.GameSetup;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.fluid.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** How a repair sees each farm spot: still the farm's, missing, which it fills, or changed by the player, which it keeps. */
class RepairSurveyTest {
	private static final BlockState HOPPER = Blocks.HOPPER.getDefaultState();

	@BeforeAll
	static void setUp() {
		GameSetup.bootstrap();
	}

	@Test
	void theFarmsOwnBlockIsFarm() {
		assertEquals(FarmAreas.Spot.FARM, FarmAreas.spot(HOPPER, HOPPER));
	}

	@Test
	void whatTheGameTurnsBlocksIntoIsStillFarm() {
		assertEquals(FarmAreas.Spot.FARM, FarmAreas.spot(Blocks.DIRT.getDefaultState(), Blocks.FARMLAND.getDefaultState()));
		assertEquals(FarmAreas.Spot.FARM, FarmAreas.spot(Blocks.BAMBOO.getDefaultState(), Blocks.BAMBOO_SAPLING.getDefaultState()));
	}

	@Test
	void emptyOrReplaceableSpotsAreMissing() {
		assertEquals(FarmAreas.Spot.MISSING, FarmAreas.spot(Blocks.AIR.getDefaultState(), HOPPER));
		assertEquals(FarmAreas.Spot.MISSING, FarmAreas.spot(Blocks.GRASS.getDefaultState(), HOPPER));
		assertEquals(FarmAreas.Spot.MISSING, FarmAreas.spot(Fluids.FLOWING_WATER.getFlowing(3, false).getBlockState(), HOPPER));
	}

	@Test
	void anotherSolidBlockIsChanged() {
		assertEquals(FarmAreas.Spot.CHANGED, FarmAreas.spot(Blocks.STONE.getDefaultState(), HOPPER));
		assertEquals(FarmAreas.Spot.CHANGED, FarmAreas.spot(Blocks.CHEST.getDefaultState(), HOPPER));
	}
}
