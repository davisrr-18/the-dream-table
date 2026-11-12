package com.davisr.thedreamtable.client;

import com.davisr.thedreamtable.GameSetup;
import com.davisr.thedreamtable.farm.FarmStep;
import com.davisr.thedreamtable.farm.FarmTemplate;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Only farms with a water step get the water line on the Requirements page. */
class WaterRowTest {
	private static FarmTemplate withWater;
	private static FarmTemplate stoneOnly;

	@BeforeAll
	static void setUp() {
		GameSetup.bootstrap();
		withWater = FarmTemplate.of("with_water", List.of(
				FarmStep.block(0, 0, 0, Blocks.STONE.getDefaultState(), Items.STONE),
				FarmStep.block(0, 1, 0, Blocks.WATER.getDefaultState(), Items.WATER_BUCKET)));
		stoneOnly = FarmTemplate.of("stone_only", List.of(
				FarmStep.block(0, 0, 0, Blocks.STONE.getDefaultState(), Items.STONE)));
	}

	@Test
	void onlyFarmsWithWaterGetTheWaterLine() {
		assertTrue(WaterRow.needed(withWater));
		assertFalse(WaterRow.needed(stoneOnly));
	}
}
