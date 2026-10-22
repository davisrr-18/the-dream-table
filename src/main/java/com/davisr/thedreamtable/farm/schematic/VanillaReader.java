package com.davisr.thedreamtable.farm.schematic;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

/** Structure block files ({@code .nbt}): a palette and a list of positioned blocks. */
final class VanillaReader {
	private VanillaReader() {
	}

	static boolean accepts(NbtCompound root) {
		return root.contains("blocks", NbtElement.LIST_TYPE);
	}

	static void read(NbtCompound root, StepCollector steps) {
		NbtList palette = palette(root);
		NbtList blocks = root.getList("blocks", NbtElement.COMPOUND_TYPE);
		for (int index = 0; index < blocks.size(); index++) {
			NbtCompound entry = blocks.getCompound(index);
			NbtList pos = entry.getList("pos", NbtElement.INT_TYPE);
			if (pos.size() < 3) {
				continue;
			}
			NbtCompound nbt = entry.contains("nbt", NbtElement.COMPOUND_TYPE) ? entry.getCompound("nbt") : null;
			steps.block(pos.getInt(0), pos.getInt(1), pos.getInt(2), NbtReading.state(palette.getCompound(entry.getInt("state"))), nbt);
		}
		steps.entities(root.getList("entities", NbtElement.COMPOUND_TYPE));
	}

	private static NbtList palette(NbtCompound root) {
		if (root.contains("palette", NbtElement.LIST_TYPE)) {
			return root.getList("palette", NbtElement.COMPOUND_TYPE);
		}
		NbtList palettes = root.getList("palettes", NbtElement.LIST_TYPE);
		return palettes.isEmpty() ? new NbtList() : palettes.getList(0);
	}
}
