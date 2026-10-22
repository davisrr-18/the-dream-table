package com.davisr.thedreamtable.farm.schematic;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

import java.util.ArrayList;
import java.util.List;

/** Litematica files ({@code .litematic}): one or more regions, moved so the lowest corner sits at the origin. */
final class LitematicaReader {
	private LitematicaReader() {
	}

	static boolean accepts(NbtCompound root) {
		return root.contains("Regions", NbtElement.COMPOUND_TYPE);
	}

	static void read(NbtCompound root, StepCollector steps) {
		NbtCompound regions = root.getCompound("Regions");
		List<NbtCompound> regionList = new ArrayList<>();
		int[] origin = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE};
		for (String name : regions.getKeys()) {
			NbtCompound region = regions.getCompound(name);
			int[] min = minimumCorner(region);
			for (int axis = 0; axis < 3; axis++) {
				origin[axis] = Math.min(origin[axis], min[axis]);
			}
			regionList.add(region);
		}
		List<int[]> placed = new ArrayList<>();
		for (NbtCompound region : regionList) {
			readRegion(region, origin, steps, placed);
			if (region.contains("TileEntities", NbtElement.LIST_TYPE)) {
				attachTiles(steps, placed, region.getList("TileEntities", NbtElement.COMPOUND_TYPE));
			}
			if (region.contains("Entities", NbtElement.LIST_TYPE)) {
				steps.entities(region.getList("Entities", NbtElement.COMPOUND_TYPE));
			}
		}
	}

	/** Each placed cell is remembered as {local x, y, z, region x, y, z} so tile data can match either. */
	private static void readRegion(NbtCompound region, int[] origin, StepCollector steps, List<int[]> placed) {
		int[] min = minimumCorner(region);
		int sizeX = Math.abs(NbtReading.numberAt(region, "Size", 0));
		int sizeY = Math.abs(NbtReading.numberAt(region, "Size", 1));
		int sizeZ = Math.abs(NbtReading.numberAt(region, "Size", 2));
		NbtList palette = region.getList("BlockStatePalette", NbtElement.COMPOUND_TYPE);
		long[] packed = region.getLongArray("BlockStates");
		int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(Math.max(1, palette.size()) - 1));
		long mask = (1L << bits) - 1L;
		for (int y = 0; y < sizeY; y++) {
			for (int z = 0; z < sizeZ; z++) {
				for (int x = 0; x < sizeX; x++) {
					int id = packedId(packed, (y * sizeZ + z) * sizeX + x, bits, mask);
					if (id < 0 || id >= palette.size()) {
						continue;
					}
					int stepX = min[0] + x - origin[0];
					int stepY = min[1] + y - origin[1];
					int stepZ = min[2] + z - origin[2];
					steps.block(stepX, stepY, stepZ, NbtReading.state(palette.getCompound(id)), null);
					placed.add(new int[]{stepX, stepY, stepZ, min[0] + x, min[1] + y, min[2] + z});
				}
			}
		}
	}

	private static void attachTiles(StepCollector steps, List<int[]> placed, NbtList tiles) {
		for (int index = 0; index < tiles.size(); index++) {
			NbtCompound data = tiles.getCompound(index);
			int x = data.getInt("x");
			int y = data.getInt("y");
			int z = data.getInt("z");
			for (int[] spot : placed) {
				boolean local = spot[0] == x && spot[1] == y && spot[2] == z;
				boolean world = spot[3] == x && spot[4] == y && spot[5] == z;
				if (local || world) {
					steps.blockData(spot[0], spot[1], spot[2], data);
					break;
				}
			}
		}
	}

	/** Palette ids are packed tightly across longs, so an id may span two of them. */
	private static int packedId(long[] packed, int index, int bits, long mask) {
		if (packed.length == 0) {
			return -1;
		}
		long start = (long) index * bits;
		int startIndex = (int) (start >>> 6);
		int endIndex = (int) ((start + bits - 1) >>> 6);
		if (startIndex >= packed.length) {
			return -1;
		}
		int bit = (int) (start & 63);
		if (startIndex == endIndex) {
			return (int) ((packed[startIndex] >>> bit) & mask);
		}
		if (endIndex >= packed.length) {
			return -1;
		}
		return (int) (((packed[startIndex] >>> bit) | (packed[endIndex] << (64 - bit))) & mask);
	}

	/** A negative size means the region grows toward smaller coordinates from its position. */
	private static int[] minimumCorner(NbtCompound region) {
		int[] corner = new int[3];
		for (int axis = 0; axis < 3; axis++) {
			int pos = NbtReading.numberAt(region, "Position", axis);
			int size = NbtReading.numberAt(region, "Size", axis);
			corner[axis] = Math.min(pos, pos + size + (size < 0 ? 1 : 0));
		}
		return corner;
	}
}
