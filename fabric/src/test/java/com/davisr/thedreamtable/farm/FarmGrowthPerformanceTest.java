package com.davisr.thedreamtable.farm;

import com.davisr.thedreamtable.GameSetup;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Asking whether a step is growth must not rescan the farm: big imported schematics are asked once per step. */
class FarmGrowthPerformanceTest {
	private static final int WIDTH = 50;
	private static final int HEIGHT = 20;

	@BeforeAll
	static void setUp() {
		GameSetup.bootstrap();
	}

	private static FarmTemplate columns() {
		List<FarmStep> steps = new ArrayList<>();
		for (int x = 0; x < WIDTH; x++) {
			for (int z = 0; z < WIDTH; z++) {
				for (int y = 0; y < HEIGHT; y++) {
					steps.add(FarmStep.block(x, y, z, Blocks.SUGAR_CANE.getDefaultState(), Items.SUGAR_CANE));
				}
			}
		}
		return new FarmTemplate("columns", List.copyOf(steps), List.of(), BlockPos.ORIGIN);
	}

	@Test
	@Timeout(value = 10, unit = TimeUnit.SECONDS)
	void everyStepOfAFiftyThousandStepFarmCanBeAskedAbout() {
		FarmTemplate template = columns();
		assertEquals(WIDTH * WIDTH * HEIGHT, template.steps().size());
		int growth = 0;
		for (int step = 0; step < template.steps().size(); step++) {
			if (FarmGrowth.isGrowth(template, step)) {
				growth++;
			}
		}
		assertEquals(WIDTH * WIDTH * (HEIGHT - 1), growth, "only the lowest block of each plant is built");
	}

	@Test
	void theGrowthStepsAreFoundOncePerTemplateObject() {
		FarmTemplate template = columns();
		BitSet first = FarmGrowth.growth(template);
		assertSame(first, FarmGrowth.growth(template));
		FarmTemplate copy = new FarmTemplate(template.id(), template.steps(), template.materials(), template.center());
		assertEquals(template, copy);
		assertEquals(first, FarmGrowth.growth(copy));
	}
}
