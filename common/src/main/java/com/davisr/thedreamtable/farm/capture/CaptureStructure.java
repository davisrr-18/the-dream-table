package com.davisr.thedreamtable.farm.capture;

import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtInt;
import net.minecraft.nbt.NbtList;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Builds a vanilla structure file's data ({@code size}, {@code palette}, {@code blocks}, no entities) that {@code VanillaReader} reads back. */
public final class CaptureStructure {
	private CaptureStructure() {
	}

	public static NbtCompound toNbt(CaptureBox box, List<CaptureScan.Entry> blocks, int dataVersion) {
		NbtCompound root = new NbtCompound();
		root.put("size", ints(box.sizeX(), box.sizeY(), box.sizeZ()));
		NbtList palette = new NbtList();
		Map<BlockState, Integer> indexes = new HashMap<>();
		NbtList list = new NbtList();
		for (CaptureScan.Entry block : blocks) {
			Integer index = indexes.get(block.state());
			if (index == null) {
				index = palette.size();
				indexes.put(block.state(), index);
				palette.add(NbtHelper.fromBlockState(block.state()));
			}
			NbtCompound entry = new NbtCompound();
			entry.put("pos", ints(block.x(), block.y(), block.z()));
			entry.putInt("state", index);
			list.add(entry);
		}
		root.put("palette", palette);
		root.put("blocks", list);
		root.put("entities", new NbtList());
		root.putInt("DataVersion", dataVersion);
		return root;
	}

	private static NbtList ints(int x, int y, int z) {
		NbtList list = new NbtList();
		list.add(NbtInt.of(x));
		list.add(NbtInt.of(y));
		list.add(NbtInt.of(z));
		return list;
	}
}
