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

/** Code-built mob and iron farms. */
public final class MobFarms {
	private MobFarms() {
	}

	/** Dark room of dirt. Open trapdoors make mobs walk into a hopper line. */
	public static FarmTemplate darkRoom(String id) {
		List<FarmStep> steps = new ArrayList<>();
		for (int x = 0; x <= 9; x++) {
			for (int z = 0; z <= 9; z++) {
				boolean hopper = z == 7 && x >= 1 && x <= 8;
				boolean chest = x == 8 && z == 8;
				boolean lip = z == 6 && x >= 1 && x <= 8;
				if (!hopper && !chest && !lip) {
					dirt(steps, x, 0, z);
				}
			}
		}
		for (int x = 1; x <= 7; x++) {
			hopper(steps, x, 0, 7, Direction.EAST);
		}
		hopper(steps, 8, 0, 7, Direction.SOUTH);
		chest(steps, 8, 0, 8);
		for (int x = 1; x <= 8; x++) {
			block(steps, x, 0, 6, openTrapdoor(Direction.SOUTH), Items.OAK_TRAPDOOR);
		}
		for (int y = 1; y <= 3; y++) {
			for (int x = 0; x <= 9; x++) {
				for (int z = 0; z <= 9; z++) {
					if (x == 0 || x == 9 || z == 0 || z == 9 || y == 3) {
						dirt(steps, x, y, z);
					}
				}
			}
		}
		return FarmTemplate.of(id, steps);
	}

	/**
	 * Villagers, beds and composters above a dirt platform. A sealed water trench pushes golems onto campfires,
	 * and hoppers under the trench store the iron. Water is placed after the walls.
	 */
	public static FarmTemplate iron(String id) {
		List<FarmStep> steps = new ArrayList<>();
		platform(steps);
		for (int y = 2; y <= 4; y++) {
			dirt(steps, 2, y, 1);
			dirt(steps, 7, y, 1);
		}
		for (int x = 2; x <= 7; x++) {
			for (int z = 1; z <= 5; z++) {
				dirt(steps, x, 5, z);
			}
		}
		bed(steps, 2, 6, 2);
		bed(steps, 4, 6, 2);
		bed(steps, 6, 6, 2);
		for (int x : new int[]{3, 5, 7}) {
			block(steps, x, 6, 4, Blocks.COMPOSTER.getDefaultState(), Items.COMPOSTER);
		}
		villagerRoom(steps);
		zombieCell(steps);
		steps.add(FarmStep.entity(3, 6, 5, EntityType.VILLAGER, false));
		steps.add(FarmStep.entity(5, 6, 5, EntityType.VILLAGER, false));
		steps.add(FarmStep.entity(4, 6, 5, EntityType.VILLAGER, false));
		steps.add(FarmStep.minecart(9, 6, 4, EntityType.ZOMBIE));
		steps.add(FarmStep.block(1, 1, 4, Blocks.WATER.getDefaultState(), Items.WATER_BUCKET));
		return FarmTemplate.of(id, steps);
	}

	private static void platform(List<FarmStep> steps) {
		for (int x = 0; x <= 9; x++) {
			for (int z = 0; z <= 9; z++) {
				boolean trench = z == 4 && x >= 1 && x <= 8;
				boolean chest = x == 8 && z == 5;
				if (!trench && !chest) {
					dirt(steps, x, 0, z);
				}
				if (!trench && z != 3 && z != 4 && z != 5) {
					block(steps, x, 1, z, floorButton(), Items.OAK_BUTTON);
				}
			}
		}
		for (int x = 1; x <= 7; x++) {
			hopper(steps, x, 0, 4, Direction.EAST);
		}
		hopper(steps, 8, 0, 4, Direction.SOUTH);
		chest(steps, 8, 0, 5);
		block(steps, 8, 1, 4, Blocks.CAMPFIRE.getDefaultState().with(Properties.LIT, true), Items.CAMPFIRE);
		dirt(steps, 0, 1, 4);
		dirt(steps, 9, 1, 4);
		for (int x = 0; x <= 9; x++) {
			dirt(steps, x, 1, 3);
			dirt(steps, x, 1, 5);
		}
	}

	private static void villagerRoom(List<FarmStep> steps) {
		for (int y = 6; y <= 7; y++) {
			for (int x = 1; x <= 8; x++) {
				for (int z = 1; z <= 6; z++) {
					boolean shell = x == 1 || x == 8 || z == 1 || z == 6;
					boolean gap = x == 8 && z == 4 && y == 7;
					if (shell && !gap) {
						dirt(steps, x, y, z);
					}
				}
			}
		}
		for (int x = 1; x <= 8; x++) {
			for (int z = 1; z <= 6; z++) {
				dirt(steps, x, 8, z);
			}
		}
	}

	private static void zombieCell(List<FarmStep> steps) {
		dirt(steps, 9, 5, 4);
		dirt(steps, 9, 6, 3);
		dirt(steps, 9, 6, 5);
		dirt(steps, 10, 6, 4);
		dirt(steps, 9, 7, 3);
		dirt(steps, 9, 7, 5);
		dirt(steps, 10, 7, 4);
		dirt(steps, 9, 8, 4);
	}
}
