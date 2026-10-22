package com.davisr.thedreamtable.farm;

import net.minecraft.block.BambooBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.BambooLeaves;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.BlockFace;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.RailShape;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FarmTemplates {
	private static final Map<String, FarmTemplate> TEMPLATES = new LinkedHashMap<>();
	private static final Map<String, FarmTemplate> IMPORTED = new LinkedHashMap<>();
	private static final List<FarmOptions.FarmOption> IMPORTED_OPTIONS = new ArrayList<>();

	static {
		register(sugarCane());
		register(bamboo());
		register(crop());
		register(mobDarkRoom());
		register(iron());
	}

	private FarmTemplates() {
	}

	public static void load() {
		TEMPLATES.size();
		reloadImports(null);
	}

	public static void reloadImports(java.nio.file.Path worldRoot) {
		ImportedStructures.reload(IMPORTED, IMPORTED_OPTIONS, worldRoot);
	}

	public static FarmTemplate get(String id) {
		FarmTemplate builtIn = TEMPLATES.get(id);
		return builtIn != null ? builtIn : IMPORTED.get(id);
	}

	public static List<FarmOptions.FarmOption> options() {
		List<FarmOptions.FarmOption> options = new ArrayList<>(FarmOptions.ALL);
		options.addAll(IMPORTED_OPTIONS);
		return options;
	}

	public static List<FarmOptions.FarmOption> imported() {
		return List.copyOf(IMPORTED_OPTIONS);
	}

	private static void register(FarmTemplate template) {
		TEMPLATES.put(template.id(), template);
	}

	private static FarmTemplate sugarCane() {
		return pistonRow(FarmOptions.SUGAR_CANE.id(), true);
	}

	private static FarmTemplate bamboo() {
		return pistonRow(FarmOptions.BAMBOO.id(), false);
	}

	/**
	 * Eight plants. Observers watch the third block and pistons break only the growth above the base,
	 * so the plant stays rooted. Sugar cane water is walled in before the plants go down.
	 * A hopper minecart under the soil empties into a hopper and a chest.
	 */
	private static FarmTemplate pistonRow(String id, boolean water) {
		List<FarmStep> steps = new ArrayList<>();
		List<FarmStep> fluids = new ArrayList<>();
		List<FarmStep> plants = new ArrayList<>();
		int length = 8;
		for (int x = 0; x < length; x++) {
			block(steps, x, 0, 0, Blocks.DIRT.getDefaultState(), Items.DIRT);
			block(steps, x, 0, 1, Blocks.DIRT.getDefaultState(), Items.DIRT);
			if (x == 4) {
				block(steps, x, 0, 2, Blocks.HOPPER.getDefaultState().with(Properties.HOPPER_FACING, Direction.SOUTH), Items.HOPPER);
			} else {
				block(steps, x, 0, 2, Blocks.DIRT.getDefaultState(), Items.DIRT);
			}
			block(steps, x, 1, 0, Blocks.DIRT.getDefaultState(), Items.DIRT);
			block(steps, x, 1, 1, Blocks.DIRT.getDefaultState(), Items.DIRT);
			block(steps, x, 1, 2, x == 0 || x == length - 1 ? poweredRail() : rail(), x == 0 || x == length - 1 ? Items.POWERED_RAIL : Items.RAIL);
			block(steps, x, 1, 3, Blocks.DIRT.getDefaultState(), Items.DIRT);
			block(steps, x, 1, 4, Blocks.DIRT.getDefaultState(), Items.DIRT);
			block(steps, x, 2, 0, Blocks.DIRT.getDefaultState(), Items.DIRT);
			block(steps, x, 2, 1, Blocks.DIRT.getDefaultState(), Items.DIRT);
			block(steps, x, 2, 2, Blocks.DIRT.getDefaultState(), Items.DIRT);
			block(steps, x, 2, 4, Blocks.DIRT.getDefaultState(), Items.DIRT);
			block(steps, x, 3, 0, Blocks.DIRT.getDefaultState(), Items.DIRT);
			block(steps, x, 3, 1, Blocks.DIRT.getDefaultState(), Items.DIRT);
			block(steps, x, 4, 0, Blocks.DIRT.getDefaultState(), Items.DIRT);
			if (water) {
				fluids.add(FarmStep.block(x, 2, 3, Blocks.WATER.getDefaultState(), Items.WATER_BUCKET));
			}
			plants.add(FarmStep.block(x, 3, 2, water ? Blocks.SUGAR_CANE.getDefaultState() : bambooStalk(), water ? Items.SUGAR_CANE : Items.BAMBOO));
		}
		block(steps, 4, 0, 3, Blocks.CHEST.getDefaultState(), Items.CHEST);
		block(steps, -1, 1, 2, Blocks.REDSTONE_BLOCK.getDefaultState(), Items.REDSTONE_BLOCK);
		block(steps, length, 1, 2, Blocks.REDSTONE_BLOCK.getDefaultState(), Items.REDSTONE_BLOCK);
		block(steps, -1, 1, 3, Blocks.DIRT.getDefaultState(), Items.DIRT);
		block(steps, -1, 2, 3, Blocks.DIRT.getDefaultState(), Items.DIRT);
		block(steps, length, 1, 3, Blocks.DIRT.getDefaultState(), Items.DIRT);
		block(steps, length, 2, 3, Blocks.DIRT.getDefaultState(), Items.DIRT);
		steps.addAll(fluids);
		steps.addAll(plants);
		for (int x = 0; x < length; x++) {
			block(steps, x, 4, 1, Blocks.PISTON.getDefaultState().with(Properties.FACING, Direction.SOUTH), Items.PISTON);
			block(steps, x, 5, 1, Blocks.OBSERVER.getDefaultState().with(Properties.FACING, Direction.SOUTH), Items.OBSERVER);
			block(steps, x, 5, 0, Blocks.REDSTONE_WIRE.getDefaultState(), Items.REDSTONE);
		}
		steps.add(new FarmStep(1, 1, 2, null, Items.HOPPER_MINECART, EntityType.HOPPER_MINECART, false, false, null));
		return FarmTemplate.of(id, steps);
	}

	/** Nine by five plot. Villagers harvest, a minecart under the soil feeds two chests. Water is sealed in by farmland. */
	private static FarmTemplate crop() {
		List<FarmStep> steps = new ArrayList<>();
		List<FarmStep> fluid = new ArrayList<>();
		for (int x = 0; x <= 8; x++) {
			for (int z = 0; z <= 4; z++) {
				boolean storage = (x == 0 || x == 1) && (z == 1 || z == 3);
				if (!storage) {
					block(steps, x, 0, z, Blocks.DIRT.getDefaultState(), Items.DIRT);
				}
			}
		}
		block(steps, 0, 0, 1, Blocks.CHEST.getDefaultState(), Items.CHEST);
		block(steps, 0, 0, 3, Blocks.CHEST.getDefaultState(), Items.CHEST);
		block(steps, 1, 0, 1, Blocks.HOPPER.getDefaultState().with(Properties.HOPPER_FACING, Direction.WEST), Items.HOPPER);
		block(steps, 1, 0, 3, Blocks.HOPPER.getDefaultState().with(Properties.HOPPER_FACING, Direction.WEST), Items.HOPPER);
		for (int z : new int[]{1, 3}) {
			for (int x = 1; x <= 7; x++) {
				block(steps, x, 1, z, Blocks.DIRT.getDefaultState(), Items.DIRT);
				block(steps, x, 2, z, Blocks.FARMLAND.getDefaultState(), Items.DIRT);
				if (!(z == 1 && x == 4)) {
					block(steps, x, 3, z, Blocks.WHEAT.getDefaultState(), Items.WHEAT_SEEDS);
				}
			}
		}
		for (int x = 2; x <= 6; x++) {
			block(steps, x, 1, 2, Blocks.DIRT.getDefaultState(), Items.DIRT);
			if (x == 4) {
				continue;
			}
			block(steps, x, 2, 2, Blocks.FARMLAND.getDefaultState(), Items.DIRT);
			block(steps, x, 3, 2, Blocks.WHEAT.getDefaultState(), Items.WHEAT_SEEDS);
		}
		fluid.add(FarmStep.block(4, 2, 2, Blocks.WATER.getDefaultState(), Items.WATER_BUCKET));
		block(steps, 4, 3, 1, Blocks.COMPOSTER.getDefaultState(), Items.COMPOSTER);
		for (int x = 0; x <= 8; x++) {
			for (int z = 0; z <= 4; z++) {
				if (x == 0 || x == 8 || z == 0 || z == 4) {
					block(steps, x, 2, z, Blocks.DIRT.getDefaultState(), Items.DIRT);
					block(steps, x, 3, z, Blocks.OAK_FENCE.getDefaultState(), Items.OAK_FENCE);
				}
			}
		}
		steps.add(FarmStep.entity(3, 3, 1, EntityType.VILLAGER, false));
		steps.add(FarmStep.entity(6, 3, 3, EntityType.VILLAGER, false));
		steps.addAll(fluid);
		return FarmTemplate.of(FarmOptions.CROP.id(), steps);
	}

	/** Dark room of dirt. Open trapdoors make mobs walk into a hopper line. */
	private static FarmTemplate mobDarkRoom() {
		List<FarmStep> steps = new ArrayList<>();
		for (int x = 0; x <= 9; x++) {
			for (int z = 0; z <= 9; z++) {
				boolean hopper = z == 7 && x >= 1 && x <= 8;
				boolean chest = x == 8 && z == 8;
				boolean lip = z == 6 && x >= 1 && x <= 8;
				if (!hopper && !chest && !lip) {
					block(steps, x, 0, z, Blocks.DIRT.getDefaultState(), Items.DIRT);
				}
			}
		}
		for (int x = 1; x <= 7; x++) {
			block(steps, x, 0, 7, Blocks.HOPPER.getDefaultState().with(Properties.HOPPER_FACING, Direction.EAST), Items.HOPPER);
		}
		block(steps, 8, 0, 7, Blocks.HOPPER.getDefaultState().with(Properties.HOPPER_FACING, Direction.SOUTH), Items.HOPPER);
		block(steps, 8, 0, 8, Blocks.CHEST.getDefaultState(), Items.CHEST);
		for (int x = 1; x <= 8; x++) {
			block(steps, x, 0, 6, openTrapdoor(Direction.SOUTH), Items.OAK_TRAPDOOR);
		}
		for (int y = 1; y <= 3; y++) {
			for (int x = 0; x <= 9; x++) {
				for (int z = 0; z <= 9; z++) {
					if (x == 0 || x == 9 || z == 0 || z == 9 || y == 3) {
						block(steps, x, y, z, Blocks.DIRT.getDefaultState(), Items.DIRT);
					}
				}
			}
		}
		return FarmTemplate.of(FarmOptions.MOB_DARK_ROOM.id(), steps);
	}

	/**
	 * Villagers, beds and composters above a dirt platform. A sealed water trench pushes golems onto campfires,
	 * and hoppers under the trench store the iron. Water is placed after the walls.
	 */
	private static FarmTemplate iron() {
		List<FarmStep> steps = new ArrayList<>();
		List<FarmStep> fluid = new ArrayList<>();
		for (int x = 0; x <= 9; x++) {
			for (int z = 0; z <= 9; z++) {
				boolean trench = z == 4 && x >= 1 && x <= 8;
				boolean chest = x == 8 && z == 5;
				if (!trench && !chest) {
					block(steps, x, 0, z, Blocks.DIRT.getDefaultState(), Items.DIRT);
				}
				if (!trench && z != 3 && z != 4 && z != 5) {
					block(steps, x, 1, z, button(), Items.OAK_BUTTON);
				}
			}
		}
		for (int x = 1; x <= 7; x++) {
			block(steps, x, 0, 4, Blocks.HOPPER.getDefaultState().with(Properties.HOPPER_FACING, Direction.EAST), Items.HOPPER);
		}
		block(steps, 8, 0, 4, Blocks.HOPPER.getDefaultState().with(Properties.HOPPER_FACING, Direction.SOUTH), Items.HOPPER);
		block(steps, 8, 0, 5, Blocks.CHEST.getDefaultState(), Items.CHEST);
		block(steps, 8, 1, 4, Blocks.CAMPFIRE.getDefaultState().with(Properties.LIT, true), Items.CAMPFIRE);
		block(steps, 0, 1, 4, Blocks.DIRT.getDefaultState(), Items.DIRT);
		block(steps, 9, 1, 4, Blocks.DIRT.getDefaultState(), Items.DIRT);
		for (int x = 0; x <= 9; x++) {
			block(steps, x, 1, 3, Blocks.DIRT.getDefaultState(), Items.DIRT);
			block(steps, x, 1, 5, Blocks.DIRT.getDefaultState(), Items.DIRT);
		}
		fluid.add(FarmStep.block(1, 1, 4, Blocks.WATER.getDefaultState(), Items.WATER_BUCKET));
		for (int y = 2; y <= 4; y++) {
			block(steps, 2, y, 1, Blocks.DIRT.getDefaultState(), Items.DIRT);
			block(steps, 7, y, 1, Blocks.DIRT.getDefaultState(), Items.DIRT);
		}
		for (int x = 2; x <= 7; x++) {
			for (int z = 1; z <= 5; z++) {
				block(steps, x, 5, z, Blocks.DIRT.getDefaultState(), Items.DIRT);
			}
		}
		bed(steps, 2, 6, 2);
		bed(steps, 4, 6, 2);
		bed(steps, 6, 6, 2);
		block(steps, 3, 6, 4, Blocks.COMPOSTER.getDefaultState(), Items.COMPOSTER);
		block(steps, 5, 6, 4, Blocks.COMPOSTER.getDefaultState(), Items.COMPOSTER);
		block(steps, 7, 6, 4, Blocks.COMPOSTER.getDefaultState(), Items.COMPOSTER);
		for (int y = 6; y <= 7; y++) {
			for (int x = 1; x <= 8; x++) {
				for (int z = 1; z <= 6; z++) {
					boolean shell = x == 1 || x == 8 || z == 1 || z == 6;
					boolean gap = x == 8 && z == 4 && y == 7;
					if (shell && !gap) {
						block(steps, x, y, z, Blocks.DIRT.getDefaultState(), Items.DIRT);
					}
				}
			}
		}
		for (int x = 1; x <= 8; x++) {
			for (int z = 1; z <= 6; z++) {
				block(steps, x, 8, z, Blocks.DIRT.getDefaultState(), Items.DIRT);
			}
		}
		block(steps, 9, 5, 4, Blocks.DIRT.getDefaultState(), Items.DIRT);
		block(steps, 9, 6, 3, Blocks.DIRT.getDefaultState(), Items.DIRT);
		block(steps, 9, 6, 5, Blocks.DIRT.getDefaultState(), Items.DIRT);
		block(steps, 10, 6, 4, Blocks.DIRT.getDefaultState(), Items.DIRT);
		block(steps, 9, 7, 3, Blocks.DIRT.getDefaultState(), Items.DIRT);
		block(steps, 9, 7, 5, Blocks.DIRT.getDefaultState(), Items.DIRT);
		block(steps, 10, 7, 4, Blocks.DIRT.getDefaultState(), Items.DIRT);
		block(steps, 9, 8, 4, Blocks.DIRT.getDefaultState(), Items.DIRT);
		steps.add(FarmStep.entity(3, 6, 5, EntityType.VILLAGER, false));
		steps.add(FarmStep.entity(5, 6, 5, EntityType.VILLAGER, false));
		steps.add(FarmStep.entity(4, 6, 5, EntityType.VILLAGER, false));
		steps.add(FarmStep.minecart(9, 6, 4, EntityType.ZOMBIE));
		steps.addAll(fluid);
		return FarmTemplate.of(FarmOptions.IRON_GOLEM.id(), steps);
	}

	private static void bed(List<FarmStep> steps, int x, int y, int z) {
		BlockState foot = Blocks.RED_BED.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.SOUTH).with(Properties.BED_PART, BedPart.FOOT);
		BlockState head = Blocks.RED_BED.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.SOUTH).with(Properties.BED_PART, BedPart.HEAD);
		block(steps, x, y, z, foot, Items.RED_BED);
		block(steps, x, y, z + 1, head, null);
	}

	private static BlockState bambooStalk() {
		return Blocks.BAMBOO.getDefaultState()
				.with(BambooBlock.AGE, 0)
				.with(BambooBlock.STAGE, 0)
				.with(BambooBlock.LEAVES, BambooLeaves.NONE);
	}

	private static BlockState rail() {
		return Blocks.RAIL.getDefaultState().with(Properties.RAIL_SHAPE, RailShape.EAST_WEST);
	}

	private static BlockState poweredRail() {
		return Blocks.POWERED_RAIL.getDefaultState().with(Properties.STRAIGHT_RAIL_SHAPE, RailShape.EAST_WEST);
	}

	private static BlockState wallTorch(Direction facing) {
		return Blocks.REDSTONE_WALL_TORCH.getDefaultState().with(Properties.HORIZONTAL_FACING, facing);
	}

	private static BlockState openTrapdoor(Direction facing) {
		return Blocks.OAK_TRAPDOOR.getDefaultState()
				.with(Properties.HORIZONTAL_FACING, facing)
				.with(Properties.OPEN, true)
				.with(Properties.BLOCK_HALF, BlockHalf.BOTTOM);
	}

	private static BlockState button() {
		return Blocks.OAK_BUTTON.getDefaultState().with(Properties.BLOCK_FACE, BlockFace.FLOOR).with(Properties.HORIZONTAL_FACING, Direction.NORTH);
	}

	private static void block(List<FarmStep> steps, int x, int y, int z, BlockState state, Item item) {
		steps.add(FarmStep.block(x, y, z, state, item));
	}
}
