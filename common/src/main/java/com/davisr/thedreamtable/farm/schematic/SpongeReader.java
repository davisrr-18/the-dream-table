package com.davisr.thedreamtable.farm.schematic;

import com.davisr.thedreamtable.Nbts;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;


/** WorldEdit Sponge schematics ({@code .schem}), version 2 with flat keys and version 3 under {@code Blocks}. */
final class SpongeReader {
	private SpongeReader() {
	}

	static boolean accepts(NbtCompound root) {
		return root.getByteArray("BlockData").isPresent() || nested(root);
	}

	static void read(NbtCompound root, StepCollector steps) {
		NbtCompound blocks = nested(root) ? root.getCompoundOrEmpty("Blocks") : root;
		int width = NbtReading.number(root, "Width");
		int height = NbtReading.number(root, "Height");
		int length = NbtReading.number(root, "Length");
		if (width <= 0 || height <= 0 || length <= 0) {
			return;
		}
		// Every side is checked, so the report names the longest; a file within the span still has its cells counted.
		if (!(steps.side(width) & steps.side(height) & steps.side(length)) || !steps.reserve(width, height, length)) {
			return;
		}
		NbtCompound paletteNbt = Nbts.hasCompound(blocks, "Palette") ? blocks.getCompoundOrEmpty("Palette") : root.getCompoundOrEmpty("Palette");
		String[] names = names(paletteNbt);
		if (names == null) {
			steps.overflow();
			return;
		}
		BlockState[] palette = new BlockState[names.length];
		for (int id = 0; id < names.length; id++) {
			palette[id] = names[id] == null ? Blocks.AIR.getDefaultState() : NbtReading.state(names[id], steps.dataVersion());
		}
		byte[] data = blocks.getByteArray("Data").orElseGet(() -> root.getByteArray("BlockData").orElseGet(() -> new byte[0]));
		int[] cursor = {0};
		readCells(steps, palette, names, data, cursor, width, height, length);
		steps.blockEntities(blockEntities(root, blocks));
		steps.entities(Nbts.list(root, "Entities", NbtCompound.class));
	}

	/** Cells are stored y, then z, then x, each as a palette varint. A null palette state is a block this game doesn't have. */
	private static void readCells(StepCollector steps, BlockState[] palette, String[] names, byte[] data, int[] cursor, int width, int height, int length) {
		for (int y = 0; y < height; y++) {
			for (int z = 0; z < length; z++) {
				for (int x = 0; x < width; x++) {
					if (cursor[0] >= data.length) {
						return;
					}
					int id = varInt(data, cursor);
					if (id >= 0 && id < palette.length) {
						if (palette[id] == null) {
							steps.unknown(NbtReading.name(names[id]));
						} else {
							steps.block(x, y, z, palette[id], null);
						}
					}
				}
			}
		}
	}

	private static boolean nested(NbtCompound root) {
		return Nbts.hasCompound(root, "Blocks") && root.getCompoundOrEmpty("Blocks").getByteArray("Data").isPresent();
	}

	private static NbtList blockEntities(NbtCompound root, NbtCompound blocks) {
		if (Nbts.hasList(blocks, "BlockEntities")) {
			return Nbts.list(blocks, "BlockEntities", NbtCompound.class);
		}
		if (Nbts.hasList(root, "BlockEntities")) {
			return Nbts.list(root, "BlockEntities", NbtCompound.class);
		}
		return Nbts.list(root, "TileEntities", NbtCompound.class);
	}

	/** The palette key for each id; ids nothing uses stay null and read as air. Null when an id passes {@link SchematicReader#MAX_PALETTE}. */
	private static String[] names(NbtCompound palette) {
		int size = 0;
		for (String key : palette.getKeys()) {
			size = Math.max(size, NbtReading.number(palette, key) + 1);
		}
		if (size > SchematicReader.MAX_PALETTE) {
			return null;
		}
		String[] names = new String[Math.max(1, size)];
		for (String key : palette.getKeys()) {
			int id = NbtReading.number(palette, key);
			if (id >= 0 && id < names.length) {
				names[id] = key;
			}
		}
		return names;
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
