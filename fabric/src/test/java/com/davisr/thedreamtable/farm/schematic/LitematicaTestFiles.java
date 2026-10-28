package com.davisr.thedreamtable.farm.schematic;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;

/** Builds Litematica regions for the reader tests. */
final class LitematicaTestFiles {
	private LitematicaTestFiles() {
	}

	static NbtList palette(String... names) {
		NbtList palette = new NbtList();
		for (String name : names) {
			NbtCompound entry = new NbtCompound();
			entry.putString("Name", name);
			palette.add(entry);
		}
		return palette;
	}

	/** Palette ids packed {@code bits} apart, the first id in the lowest bits, as Litematica stores them. */
	static long[] pack(int bits, int... ids) {
		long[] packed = new long[(ids.length * bits + 63) / 64];
		for (int index = 0; index < ids.length; index++) {
			long start = (long) index * bits;
			int low = (int) (start >>> 6);
			int shift = (int) (start & 63);
			packed[low] |= (long) ids[index] << shift;
			if (shift + bits > 64) {
				packed[low + 1] |= (long) ids[index] >>> (64 - shift);
			}
		}
		return packed;
	}

	static NbtCompound region(int[] position, int[] size, long[] states, String... palette) {
		NbtCompound region = new NbtCompound();
		region.putIntArray("Position", position);
		region.putIntArray("Size", size);
		region.put("BlockStatePalette", palette(palette));
		region.putLongArray("BlockStates", states);
		return region;
	}

	static NbtCompound tile(int x, int y, int z, String name) {
		NbtCompound tile = new NbtCompound();
		tile.putInt("x", x);
		tile.putInt("y", y);
		tile.putInt("z", z);
		tile.putString("CustomName", name);
		return tile;
	}
}
