package com.davisr.thedreamtable.farm.legacy;

import com.davisr.thedreamtable.farm.FarmStep;
import com.davisr.thedreamtable.farm.FarmTemplate;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Items;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.List;

import static com.davisr.thedreamtable.farm.legacy.Blueprint.*;

/** Code-built sugar cane, bamboo and crop farms. */
public final class PlantFarms {
	private static final int ROW_LENGTH = 8;

	private PlantFarms() {
	}

	/**
	 * Eight plants. Observers watch the third block and pistons break only the growth above the base,
	 * so the plant stays rooted. Sugar cane water is walled in before the plants go down.
	 * A hopper minecart under the soil empties into a hopper and a chest.
	 */
	public static FarmTemplate pistonRow(String id, boolean water) {
		List<FarmStep> steps = new ArrayList<>();
		List<FarmStep> fluids = new ArrayList<>();
		List<FarmStep> plants = new ArrayList<>();
		for (int x = 0; x < ROW_LENGTH; x++) {
			rowBase(steps, x);
			if (water) {
				fluids.add(FarmStep.block(x, 2, 3, Blocks.WATER.getDefaultState(), Items.WATER_BUCKET));
			}
			plants.add(FarmStep.block(x, 3, 2, water ? Blocks.SUGAR_CANE.getDefaultState() : bambooStalk(), water ? Items.SUGAR_CANE : Items.BAMBOO));
		}
		chest(steps, 4, 0, 3);
		block(steps, -1, 1, 2, Blocks.REDSTONE_BLOCK.getDefaultState(), Items.REDSTONE_BLOCK);
		block(steps, ROW_LENGTH, 1, 2, Blocks.REDSTONE_BLOCK.getDefaultState(), Items.REDSTONE_BLOCK);
		dirt(steps, -1, 1, 3);
		dirt(steps, -1, 2, 3);
		dirt(steps, ROW_LENGTH, 1, 3);
		dirt(steps, ROW_LENGTH, 2, 3);
		steps.addAll(fluids);
		steps.addAll(plants);
		for (int x = 0; x < ROW_LENGTH; x++) {
			block(steps, x, 4, 1, Blocks.PISTON.getDefaultState().with(Properties.FACING, Direction.SOUTH), Items.PISTON);
			block(steps, x, 5, 1, Blocks.OBSERVER.getDefaultState().with(Properties.FACING, Direction.SOUTH), Items.OBSERVER);
			block(steps, x, 5, 0, Blocks.REDSTONE_WIRE.getDefaultState(), Items.REDSTONE);
		}
		steps.add(new FarmStep(1, 1, 2, null, Items.HOPPER_MINECART, EntityType.HOPPER_MINECART, false, false, null));
		return FarmTemplate.of(id, steps);
	}

	private static void rowBase(List<FarmStep> steps, int x) {
		boolean end = x == 0 || x == ROW_LENGTH - 1;
		dirt(steps, x, 0, 0);
		dirt(steps, x, 0, 1);
		if (x == 4) {
			hopper(steps, x, 0, 2, Direction.SOUTH);
		} else {
			dirt(steps, x, 0, 2);
		}
		dirt(steps, x, 1, 0);
		dirt(steps, x, 1, 1);
		block(steps, x, 1, 2, end ? poweredRail() : rail(), end ? Items.POWERED_RAIL : Items.RAIL);
		dirt(steps, x, 1, 3);
		dirt(steps, x, 1, 4);
		dirt(steps, x, 2, 0);
		dirt(steps, x, 2, 1);
		dirt(steps, x, 2, 2);
		dirt(steps, x, 2, 4);
		dirt(steps, x, 3, 0);
		dirt(steps, x, 3, 1);
		dirt(steps, x, 4, 0);
	}

	/** Nine by five plot. Villagers harvest, a minecart under the soil feeds two chests. Water is sealed in by farmland. */
	public static FarmTemplate crop(String id) {
		List<FarmStep> steps = new ArrayList<>();
		for (int x = 0; x <= 8; x++) {
			for (int z = 0; z <= 4; z++) {
				boolean storage = (x == 0 || x == 1) && (z == 1 || z == 3);
				if (!storage) {
					dirt(steps, x, 0, z);
				}
			}
		}
		chest(steps, 0, 0, 1);
		chest(steps, 0, 0, 3);
		hopper(steps, 1, 0, 1, Direction.WEST);
		hopper(steps, 1, 0, 3, Direction.WEST);
		for (int z : new int[]{1, 3}) {
			for (int x = 1; x <= 7; x++) {
				dirt(steps, x, 1, z);
				block(steps, x, 2, z, Blocks.FARMLAND.getDefaultState(), Items.DIRT);
				if (!(z == 1 && x == 4)) {
					block(steps, x, 3, z, Blocks.WHEAT.getDefaultState(), Items.WHEAT_SEEDS);
				}
			}
		}
		for (int x = 2; x <= 6; x++) {
			dirt(steps, x, 1, 2);
			if (x != 4) {
				block(steps, x, 2, 2, Blocks.FARMLAND.getDefaultState(), Items.DIRT);
				block(steps, x, 3, 2, Blocks.WHEAT.getDefaultState(), Items.WHEAT_SEEDS);
			}
		}
		block(steps, 4, 3, 1, Blocks.COMPOSTER.getDefaultState(), Items.COMPOSTER);
		for (int x = 0; x <= 8; x++) {
			for (int z = 0; z <= 4; z++) {
				if (x == 0 || x == 8 || z == 0 || z == 4) {
					dirt(steps, x, 2, z);
					block(steps, x, 3, z, Blocks.OAK_FENCE.getDefaultState(), Items.OAK_FENCE);
				}
			}
		}
		steps.add(FarmStep.entity(3, 3, 1, EntityType.VILLAGER, false));
		steps.add(FarmStep.entity(6, 3, 3, EntityType.VILLAGER, false));
		steps.add(FarmStep.block(4, 2, 2, Blocks.WATER.getDefaultState(), Items.WATER_BUCKET));
		return FarmTemplate.of(id, steps);
	}
}
