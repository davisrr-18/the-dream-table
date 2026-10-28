package com.davisr.thedreamtable.farm.schematic;

import com.davisr.thedreamtable.farm.FarmStep;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.InfestedBlock;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Gathers the build steps while a format is read, skipping what the table never places, and notes what it couldn't
 * use: blocks of mods that aren't installed, and blocks a survival player can't get.
 */
final class StepCollector {
	/** Blocks with no survival way to get them, besides infested ones. */
	private static final Set<Block> CREATIVE_ONLY = Set.of(
			Blocks.BEDROCK, Blocks.BARRIER, Blocks.LIGHT, Blocks.COMMAND_BLOCK, Blocks.CHAIN_COMMAND_BLOCK, Blocks.REPEATING_COMMAND_BLOCK,
			Blocks.SPAWNER, Blocks.TRIAL_SPAWNER, Blocks.VAULT, Blocks.REINFORCED_DEEPSLATE, Blocks.END_PORTAL_FRAME, Blocks.BUDDING_AMETHYST,
			Blocks.PETRIFIED_OAK_SLAB, Blocks.CHORUS_PLANT, Blocks.FROGSPAWN, Blocks.SUSPICIOUS_SAND, Blocks.SUSPICIOUS_GRAVEL, Blocks.END_PORTAL,
			Blocks.END_GATEWAY);
	/** Blocks that only exist while something else is happening, so placing them alone makes no sense. */
	private static final Set<Block> TECHNICAL = Set.of(Blocks.MOVING_PISTON, Blocks.BUBBLE_COLUMN);

	/** Block entity keys that hold items; a player's file loses them so a built chest, hopper or lectern isn't a free stack. */
	private static final List<String> CONTENT_KEYS = List.of("Items", "RecordItem", "Book", "item");

	private final boolean stripContents;
	private final List<FarmStep> steps = new ArrayList<>();
	/** Where each block step is in {@link #steps}, so block data finds its block at once in big files. */
	private final Map<Long, Integer> blockAt = new HashMap<>();
	private final Set<String> unknownNames = new LinkedHashSet<>();
	private int unknownBlocks;
	private final Set<String> creativeNames = new LinkedHashSet<>();

	/** @param stripContents true for a player's file; bundled farms keep the items their designer put in. */
	StepCollector(boolean stripContents) {
		this.stripContents = stripContents;
	}

	/** A copy of the data, without the held items when this collector strips them. */
	private NbtCompound copyOf(NbtCompound data) {
		NbtCompound copy = data.copy();
		if (stripContents) {
			CONTENT_KEYS.forEach(copy::remove);
		}
		return copy;
	}

	List<FarmStep> steps() {
		return steps;
	}

	int unknownBlocks() {
		return unknownBlocks;
	}

	Set<String> unknownNames() {
		return unknownNames;
	}

	Set<String> creativeNames() {
		return creativeNames;
	}

	/** A cell whose block isn't in this game, like one from a mod that isn't installed. It is left out. */
	void unknown(String name) {
		unknownBlocks++;
		unknownNames.add(name);
	}

	/** Air and structure helpers are skipped, and so is flowing liquid: it comes from the source block. A null state is an unknown block. */
	void block(int x, int y, int z, BlockState state, NbtCompound blockNbt) {
		if (state == null || state.isAir() || state.isOf(Blocks.STRUCTURE_VOID) || state.isOf(Blocks.JIGSAW) || state.isOf(Blocks.STRUCTURE_BLOCK)
				|| TECHNICAL.contains(state.getBlock())) {
			return;
		}
		boolean liquid = state.isOf(Blocks.WATER) || state.isOf(Blocks.LAVA);
		if (liquid && state.contains(Properties.LEVEL_15) && state.get(Properties.LEVEL_15) != 0) {
			return;
		}
		if (CREATIVE_ONLY.contains(state.getBlock()) || state.getBlock() instanceof InfestedBlock) {
			creativeNames.add(Registries.BLOCK.getId(state.getBlock()).toString());
		}
		blockAt.put(BlockPos.asLong(x, y, z), steps.size());
		steps.add(FarmStep.block(x, y, z, state, BlockItems.itemFor(state), blockNbt == null ? null : copyOf(blockNbt)));
	}

	/** Attaches block entity data to the block at that spot. Returns false when no block is there. */
	boolean blockData(int x, int y, int z, NbtCompound data) {
		Integer index = blockAt.get(BlockPos.asLong(x, y, z));
		if (index == null) {
			return false;
		}
		FarmStep step = steps.get(index);
		steps.set(index, FarmStep.block(x, y, z, step.state(), step.item(), copyOf(data)));
		return true;
	}

	/** Block entity lists keep the position either in {@code Pos} or in separate x, y and z keys. */
	void blockEntities(NbtList blockEntities) {
		for (int index = 0; index < blockEntities.size(); index++) {
			NbtCompound data = blockEntities.getCompound(index);
			int[] pos = data.contains("Pos", NbtElement.INT_ARRAY_TYPE) ? data.getIntArray("Pos") : new int[]{data.getInt("x"), data.getInt("y"), data.getInt("z")};
			if (pos.length >= 3) {
				blockData(pos[0], pos[1], pos[2], data);
			}
		}
	}

	void entities(NbtList entities) {
		for (int index = 0; index < entities.size(); index++) {
			FarmStep step = entity(entities.getCompound(index));
			if (step != null) {
				steps.add(step);
			}
		}
	}

	private static FarmStep entity(NbtCompound entry) {
		NbtCompound data = entry.contains("nbt", NbtElement.COMPOUND_TYPE) ? entry.getCompound("nbt") : entry;
		Optional<EntityType<?>> type = EntityType.get(data.contains("id") ? data.getString("id") : entry.getString("Id"));
		int[] pos = entityPos(entry);
		if (type.isEmpty() || pos == null) {
			return null;
		}
		return FarmStep.entity(pos[0], pos[1], pos[2], type.get(), false).withItem(BlockItems.cost(type.get()));
	}

	private static int[] entityPos(NbtCompound entry) {
		if (entry.contains("blockPos", NbtElement.LIST_TYPE)) {
			NbtList pos = entry.getList("blockPos", NbtElement.INT_TYPE);
			return pos.size() < 3 ? null : new int[]{pos.getInt(0), pos.getInt(1), pos.getInt(2)};
		}
		if (entry.contains("Pos", NbtElement.LIST_TYPE)) {
			NbtList pos = entry.getList("Pos", NbtElement.DOUBLE_TYPE);
			return pos.size() < 3 ? null : new int[]{(int) Math.floor(pos.getDouble(0)), (int) Math.floor(pos.getDouble(1)), (int) Math.floor(pos.getDouble(2))};
		}
		return null;
	}
}
