package com.davisr.thedreamtable.farm.schematic;

import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

import java.util.ArrayList;
import java.util.List;

/** Litematica files ({@code .litematic}): one or more regions, moved so the lowest corner sits at the origin. */
final class LitematicaReader {
	/** Litematica projects hold a handful of regions; more is a crafted file. */
	private static final int MAX_REGIONS = 1024;

	private LitematicaReader() {
	}

	static boolean accepts(NbtCompound root) {
		return root.contains("Regions", NbtElement.COMPOUND_TYPE);
	}

	static void read(NbtCompound root, StepCollector steps) {
		NbtCompound regions = root.getCompound("Regions");
		if (regions.getKeys().size() > MAX_REGIONS) {
			steps.overflow();
			return;
		}
		List<NbtCompound> regionList = new ArrayList<>();
		long[] origin = {Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE};
		long[] far = {Long.MIN_VALUE, Long.MIN_VALUE, Long.MIN_VALUE};
		for (String name : regions.getKeys()) {
			NbtCompound region = regions.getCompound(name);
			long[] min = minimumCorner(region);
			for (int axis = 0; axis < 3; axis++) {
				origin[axis] = Math.min(origin[axis], min[axis]);
				far[axis] = Math.max(far[axis], min[axis] + size(region, axis));
			}
			regionList.add(region);
		}
		boolean fits = true;
		for (int axis = 0; axis < 3 && !regionList.isEmpty(); axis++) {
			// Every axis is checked so the report names the longest side.
			fits &= steps.side(far[axis] - origin[axis]);
		}
		if (!fits) {
			return;
		}
		for (NbtCompound region : regionList) {
			readRegion(region, origin, steps);
			if (steps.oversized()) {
				return;
			}
			if (region.contains("TileEntities", NbtElement.LIST_TYPE)) {
				attachTiles(steps, region, origin, region.getList("TileEntities", NbtElement.COMPOUND_TYPE));
			}
			if (region.contains("Entities", NbtElement.LIST_TYPE)) {
				// Entity positions are relative to the region's Position, which is not its lowest corner when a size is negative.
				steps.entities(region.getList("Entities", NbtElement.COMPOUND_TYPE), (int) (position(region, 0) - origin[0]),
						(int) (position(region, 1) - origin[1]), (int) (position(region, 2) - origin[2]));
			}
		}
	}

	/** The palette is read once; each cell then only looks its id up. */
	private static void readRegion(NbtCompound region, long[] origin, StepCollector steps) {
		NbtList palette = region.getList("BlockStatePalette", NbtElement.COMPOUND_TYPE);
		if (palette.size() > SchematicReader.MAX_PALETTE) {
			steps.overflow();
			return;
		}
		if (!steps.reserve(size(region, 0), size(region, 1), size(region, 2))) {
			return;
		}
		long[] min = minimumCorner(region);
		int sizeX = (int) size(region, 0);
		int sizeY = (int) size(region, 1);
		int sizeZ = (int) size(region, 2);
		// An empty axis holds no cells, but the outer loops would still turn over the other two.
		if (sizeX == 0 || sizeY == 0 || sizeZ == 0) {
			return;
		}
		long[] packed = region.getLongArray("BlockStates");
		int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(Math.max(1, palette.size()) - 1));
		long mask = (1L << bits) - 1L;
		long stored = packed.length * 64L / bits;
		BlockState[] states = new BlockState[palette.size()];
		for (int id = 0; id < states.length; id++) {
			states[id] = NbtReading.state(palette.getCompound(id));
		}
		int index = 0;
		for (int y = 0; y < sizeY; y++) {
			for (int z = 0; z < sizeZ; z++) {
				for (int x = 0; x < sizeX; x++, index++) {
					if (index >= stored) {
						return;
					}
					int id = packedId(packed, index, bits, mask);
					if (id < 0 || id >= palette.size()) {
						continue;
					}
					if (states[id] == null) {
						steps.unknown(palette.getCompound(id).getString("Name"));
					} else {
						steps.block((int) (min[0] + x - origin[0]), (int) (min[1] + y - origin[1]), (int) (min[2] + z - origin[2]), states[id], null);
					}
				}
			}
		}
	}

	/**
	 * Tile positions are relative to the region's lowest corner, so a tile is matched through its own region, never
	 * through another region's blocks. A position outside the region comes from a file that wrote schematic
	 * coordinates: it is tried as a step position, then from the schematic's origin.
	 */
	private static void attachTiles(StepCollector steps, NbtCompound region, long[] origin, NbtList tiles) {
		long[] min = minimumCorner(region);
		for (int index = 0; index < tiles.size(); index++) {
			NbtCompound data = tiles.getCompound(index);
			int x = data.getInt("x");
			int y = data.getInt("y");
			int z = data.getInt("z");
			if (x >= 0 && x < size(region, 0) && y >= 0 && y < size(region, 1) && z >= 0 && z < size(region, 2)) {
				steps.blockData((int) (min[0] + x - origin[0]), (int) (min[1] + y - origin[1]), (int) (min[2] + z - origin[2]), data);
			} else if (!steps.blockData(x, y, z, data)) {
				steps.blockData((int) (x - origin[0]), (int) (y - origin[1]), (int) (z - origin[2]), data);
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
	private static long[] minimumCorner(NbtCompound region) {
		long[] corner = new long[3];
		for (int axis = 0; axis < 3; axis++) {
			long pos = position(region, axis);
			long size = NbtReading.numberAt(region, "Size", axis);
			corner[axis] = Math.min(pos, pos + size + (size < 0 ? 1 : 0));
		}
		return corner;
	}

	private static long position(NbtCompound region, int axis) {
		return NbtReading.numberAt(region, "Position", axis);
	}

	/** The extent on an axis, as a long because the absolute value of the smallest int doesn't fit an int. */
	private static long size(NbtCompound region, int axis) {
		return Math.abs((long) NbtReading.numberAt(region, "Size", axis));
	}
}
