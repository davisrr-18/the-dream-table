package com.davisr.thedreamtable.farm.schematic;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

import java.util.Arrays;

/** WorldEdit Sponge schematics ({@code .schem}), version 2 with flat keys and version 3 under {@code Blocks}. */
final class SpongeReader {
	private SpongeReader() {
	}

	static boolean accepts(NbtCompound root) {
		return root.contains("BlockData", NbtElement.BYTE_ARRAY_TYPE) || nested(root);
	}

	static void read(NbtCompound root, StepCollector steps) {
		NbtCompound blocks = nested(root) ? root.getCompound("Blocks") : root;
		int width = NbtReading.number(root, "Width");
		int height = NbtReading.number(root, "Height");
		int length = NbtReading.number(root, "Length");
		if (width <= 0 || height <= 0 || length <= 0) {
			return;
		}
		BlockState[] palette = palette(blocks.contains("Palette", NbtElement.COMPOUND_TYPE) ? blocks.getCompound("Palette") : root.getCompound("Palette"));
		byte[] data = blocks.contains("Data", NbtElement.BYTE_ARRAY_TYPE) ? blocks.getByteArray("Data") : root.getByteArray("BlockData");
		int[] cursor = {0};
		readCells(steps, palette, data, cursor, width, height, length);
		steps.blockEntities(blockEntities(root, blocks));
		steps.entities(root.getList("Entities", NbtElement.COMPOUND_TYPE));
	}

	/** Cells are stored y, then z, then x, each as a palette varint. */
	private static void readCells(StepCollector steps, BlockState[] palette, byte[] data, int[] cursor, int width, int height, int length) {
		for (int y = 0; y < height; y++) {
			for (int z = 0; z < length; z++) {
				for (int x = 0; x < width; x++) {
					if (cursor[0] >= data.length) {
						return;
					}
					int id = varInt(data, cursor);
					if (id >= 0 && id < palette.length) {
						steps.block(x, y, z, palette[id], null);
					}
				}
			}
		}
	}

	private static boolean nested(NbtCompound root) {
		return root.contains("Blocks", NbtElement.COMPOUND_TYPE) && root.getCompound("Blocks").contains("Data", NbtElement.BYTE_ARRAY_TYPE);
	}

	private static NbtList blockEntities(NbtCompound root, NbtCompound blocks) {
		if (blocks.contains("BlockEntities", NbtElement.LIST_TYPE)) {
			return blocks.getList("BlockEntities", NbtElement.COMPOUND_TYPE);
		}
		if (root.contains("BlockEntities", NbtElement.LIST_TYPE)) {
			return root.getList("BlockEntities", NbtElement.COMPOUND_TYPE);
		}
		return root.getList("TileEntities", NbtElement.COMPOUND_TYPE);
	}

	private static BlockState[] palette(NbtCompound palette) {
		int size = 0;
		for (String key : palette.getKeys()) {
			size = Math.max(size, NbtReading.number(palette, key) + 1);
		}
		BlockState[] states = new BlockState[Math.max(1, size)];
		Arrays.fill(states, Blocks.AIR.getDefaultState());
		for (String key : palette.getKeys()) {
			int id = NbtReading.number(palette, key);
			if (id >= 0 && id < states.length) {
				states[id] = NbtReading.state(key);
			}
		}
		return states;
	}

	private static int varInt(byte[] data, int[] cursor) {
		int value = 0;
		int length = 0;
		int current;
		do {
			if (cursor[0] >= data.length) {
				return 0;
			}
			current = data[cursor[0]++] & 0xFF;
			value |= (current & 127) << (length++ * 7);
		} while ((current & 128) != 0 && length < 5);
		return value;
	}
}
