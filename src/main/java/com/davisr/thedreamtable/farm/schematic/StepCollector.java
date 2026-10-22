package com.davisr.thedreamtable.farm.schematic;

import com.davisr.thedreamtable.farm.FarmStep;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.state.property.Properties;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Gathers the build steps while a format is read, skipping what the table never places. */
final class StepCollector {
	private final List<FarmStep> steps = new ArrayList<>();

	List<FarmStep> steps() {
		return steps;
	}

	/** Air and structure helpers are skipped, and so is flowing liquid: it comes from the source block. */
	void block(int x, int y, int z, BlockState state, NbtCompound blockNbt) {
		if (state.isAir() || state.isOf(Blocks.STRUCTURE_VOID) || state.isOf(Blocks.JIGSAW) || state.isOf(Blocks.STRUCTURE_BLOCK)) {
			return;
		}
		boolean liquid = state.isOf(Blocks.WATER) || state.isOf(Blocks.LAVA);
		if (liquid && state.contains(Properties.LEVEL_15) && state.get(Properties.LEVEL_15) != 0) {
			return;
		}
		steps.add(FarmStep.block(x, y, z, state, BlockItems.itemFor(state), blockNbt == null ? null : blockNbt.copy()));
	}

	void blockData(int x, int y, int z, NbtCompound data) {
		for (int index = 0; index < steps.size(); index++) {
			FarmStep step = steps.get(index);
			if (!step.isEntity() && step.x() == x && step.y() == y && step.z() == z) {
				steps.set(index, FarmStep.block(x, y, z, step.state(), step.item(), data.copy()));
				return;
			}
		}
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
