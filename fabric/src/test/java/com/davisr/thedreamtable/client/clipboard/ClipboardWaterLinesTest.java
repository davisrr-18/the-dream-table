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
	void theOrderHasNoBucketLine() {
		List<ClipboardLines.Line> order = ClipboardLines.order(withWater, item -> 0, SAME, null);
		assertTrue(order.stream().noneMatch(line -> line.stack().isOf(Items.WATER_BUCKET)));
		assertTrue(order.stream().noneMatch(line -> line.stack().isOf(Items.BUCKET)));
	}

	@Test
	void theWaterStepDoesNotSplitTheStoneRun() {
		List<ClipboardLines.Line> order = ClipboardLines.order(withWater, item -> 0, SAME, null);
		assertEquals(1, order.size());
		assertEquals(2, order.getFirst().need());
	}

	@Test
	void theWaterLineComesFirstWhenTheFarmHasWater() {
		ClipboardLines.Line water = WaterRow.line(false);
		List<ClipboardLines.Line> order = ClipboardLines.order(withWater, item -> 0, SAME, water);
		assertEquals(2, order.size());
		assertEquals(water, order.getFirst());
		assertEquals("0/1", order.getFirst().count());
		assertFalse(order.getFirst().done());
	}

	@Test
	void aNearbySourceCompletesTheWaterLine() {
		ClipboardLines.Line water = WaterRow.line(true);
		assertEquals("1/1", water.count());
		assertTrue(water.done());
		assertTrue(water.stack().isOf(Items.POTION));
		assertFalse(water.stack().isOf(Items.WATER_BUCKET));
	}

	@Test
	void theMaterialTotalsHaveNoWaterLine() {
		List<ClipboardLines.Line> totals = ClipboardLines.totals(withWater, item -> 0, SAME);
		assertEquals(1, totals.size());
		assertEquals(2, totals.getFirst().need());
		assertTrue(totals.stream().noneMatch(line -> line.stack().isOf(Items.WATER_BUCKET)));
	}

	@Test
	void onlyFarmsWithWaterGetTheWaterLine() {
		assertTrue(WaterRow.needed(withWater));
		assertFalse(WaterRow.needed(stoneOnly));
	}
}
