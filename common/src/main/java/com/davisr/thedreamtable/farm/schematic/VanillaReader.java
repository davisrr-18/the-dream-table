package com.davisr.thedreamtable.farm.schematic;

import com.davisr.thedreamtable.Nbts;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtInt;
import net.minecraft.nbt.NbtList;

/** Structure block files ({@code .nbt}): a palette and a list of positioned blocks. */
final class VanillaReader {
	private VanillaReader() {
	}

	static boolean accepts(NbtCompound root) {
		return Nbts.hasList(root, "blocks");
	}

	static void read(NbtCompound root, StepCollector steps) {
		NbtList palette = palette(root);
		NbtList blocks = Nbts.list(root, "blocks", NbtCompound.class);
		for (int index = 0; index < blocks.size(); index++) {
			NbtCompound entry = blocks.getCompoundOrEmpty(index);
			NbtList pos = Nbts.list(entry, "pos", NbtInt.class);
			if (pos.size() < 3) {
				continue;
			}
			NbtCompound nbt = Nbts.hasCompound(entry, "nbt") ? entry.getCompoundOrEmpty("nbt") : null;
			NbtCompound paletteEntry = palette.getCompoundOrEmpty(entry.getInt("state", 0));
			BlockState state = NbtReading.state(paletteEntry, steps.dataVersion());
			if (state == null) {
				steps.unknown(paletteEntry.getString("Name", ""));
			} else {
				steps.block(pos.getInt(0, 0), pos.getInt(1, 0), pos.getInt(2, 0), state, nbt);
			}
		}
		steps.entities(Nbts.list(root, "entities", NbtCompound.class));
	}

	private static NbtList palette(NbtCompound root) {
		if (Nbts.hasList(root, "palette")) {
			return Nbts.list(root, "palette", NbtCompound.class);
		}
		NbtList palettes = Nbts.list(root, "palettes", NbtList.class);
		return palettes.isEmpty() ? new NbtList() : palettes.getListOrEmpty(0);
	}
}
