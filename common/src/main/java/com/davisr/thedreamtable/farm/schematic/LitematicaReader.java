package com.davisr.thedreamtable.farm.schematic;

import net.minecraft.block.BlockState;
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
		for (NbtCompound region : regionList) {
			readRegion(region, origin, steps);
			if (region.contains("TileEntities", NbtElement.LIST_TYPE)) {
				attachTiles(steps, region, origin, region.getList("TileEntities", NbtElement.COMPOUND_TYPE));
			}
			if (region.contains("Entities", NbtElement.LIST_TYPE)) {
				steps.entities(region.getList("Entities", NbtElement.COMPOUND_TYPE));
			}
		}
	}

	/** The palette is read once; each cell then only looks its id up. */
	private static void readRegion(NbtCompound region, int[] origin, StepCollector steps) {
		int[] min = minimumCorner(region);
		int sizeX = Math.abs(NbtReading.numberAt(region, "Size", 0));
		int sizeY = Math.abs(NbtReading.numberAt(region, "Size", 1));
		int sizeZ = Math.abs(NbtReading.numberAt(region, "Size", 2));
		NbtList palette = region.getList("BlockStatePalette", NbtElement.COMPOUND_TYPE);
		long[] packed = region.getLongArray("BlockStates");
		int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(Math.max(1, palette.size()) - 1));
		long mask = (1L << bits) - 1L;
		BlockState[] states = new BlockState[palette.size()];
		for (int id = 0; id < states.length; id++) {
			states[id] = NbtReading.state(palette.getCompound(id));
		}
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
					if (states[id] == null) {
						steps.unknown(palette.getCompound(id).getString("Name"));
					} else {
						steps.block(stepX, stepY, stepZ, states[id], null);
					}
				}
			}
		}
	}

	/**
	 * Tile positions are written relative to the region in some files and in schematic coordinates in others, so each
	 * reading is tried in turn: as a step position, as a schematic position, then from the region's lowest corner.
	 */
	private static void attachTiles(StepCollector steps, NbtCompound region, int[] origin, NbtList tiles) {
		int[] min = minimumCorner(region);
		for (int index = 0; index < tiles.size(); index++) {
			NbtCompound data = tiles.getCompound(index);
			int x = data.getInt("x");
			int y = data.getInt("y");
			int z = data.getInt("z");
			if (!steps.blockData(x, y, z, data) && !steps.blockData(x - origin[0], y - origin[1], z - origin[2], data)) {
				steps.blockData(min[0] + x - origin[0], min[1] + y - origin[1], min[2] + z - origin[2], data);
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
