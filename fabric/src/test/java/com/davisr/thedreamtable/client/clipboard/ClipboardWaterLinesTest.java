package com.davisr.thedreamtable.client.clipboard;

import com.davisr.thedreamtable.GameSetup;
import com.davisr.thedreamtable.farm.FarmStep;
import com.davisr.thedreamtable.farm.FarmTemplate;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The clipboard's lines for a farm with water: no bucket line, one water line, and the blocks around the water still merge. */
class ClipboardWaterLinesTest {
	private static FarmTemplate withWater;
	private static FarmTemplate stoneOnly;

	@BeforeAll
	static void setUp() {
		GameSetup.bootstrap();
		withWater = FarmTemplate.of("with_water", List.of(
				FarmStep.block(0, 0, 0, Blocks.STONE.getDefaultState(), Items.STONE),
				FarmStep.block(0, 1, 0, Blocks.WATER.getDefaultState(), Items.WATER_BUCKET),
				FarmStep.block(1, 0, 0, Blocks.STONE.getDefaultState(), Items.STONE)));
		stoneOnly = FarmTemplate.of("stone_only", List.of(
				FarmStep.block(0, 0, 0, Blocks.STONE.getDefaultState(), Items.STONE)));
	}

	private static final UnaryOperator<Item> SAME = UnaryOperator.identity();

	@Test
	void theWaterStepIsAnOrderLineOfItsOwnBetweenTheStone() {
		List<ClipboardLines.Line> order = ClipboardLines.order(withWater, item -> 0, SAME);
		assertEquals(3, order.size());
		assertTrue(order.get(1).stack().isOf(Items.WATER_BUCKET));
		assertEquals("0/1", order.get(1).count());
	}

	@Test
	void theMaterialTotalsListTheWaterBuckets() {
		List<ClipboardLines.Line> totals = ClipboardLines.totals(withWater, item -> 0, SAME);
		assertEquals(2, totals.size());
		assertTrue(totals.stream().anyMatch(line -> line.stack().isOf(Items.WATER_BUCKET) && line.need() == 1));
	}

	@Test
	void waterBucketsDeliveredStrikeTheWaterLine() {
		List<ClipboardLines.Line> order = ClipboardLines.order(withWater, item -> item == Items.WATER_BUCKET ? 1 : 0, SAME);
		assertTrue(order.get(1).done());
	}

	@Test
	void onlyFarmsWithWaterGetTheWaterLine() {
		assertTrue(WaterRow.needed(withWater));
		assertFalse(WaterRow.needed(stoneOnly));
	}
}
