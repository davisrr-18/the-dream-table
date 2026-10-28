package com.davisr.thedreamtable.farm.schematic;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.AbstractNbtNumber;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;

/** Small readers shared by the schematic formats: block states, numbers, and coordinate arrays. */
final class NbtReading {
	private NbtReading() {
	}

	/** Reads a vanilla or Litematica palette entry: {@code {Name: "...", Properties: {...}}}. Null when the block isn't in this game. */
	static BlockState state(NbtCompound entry) {
		Block block = block(entry.getString("Name"));
		if (block == null) {
			return null;
		}
		BlockState state = block.getDefaultState();
		if (!entry.contains("Properties", NbtElement.COMPOUND_TYPE)) {
			return state;
		}
		NbtCompound properties = entry.getCompound("Properties");
		for (String key : properties.getKeys()) {
			state = with(state, block, key, properties.getString(key));
		}
		return state;
	}

	/** Reads a Sponge palette key: {@code minecraft:stairs[facing=east,half=bottom]}. Null when the block isn't in this game. */
	static BlockState state(String raw) {
		Block block = block(name(raw));
		if (block == null) {
			return null;
		}
		int bracket = raw.indexOf('[');
		BlockState state = block.getDefaultState();
		if (bracket < 0) {
			return state;
		}
		String properties = raw.substring(bracket + 1, raw.endsWith("]") ? raw.length() - 1 : raw.length());
		for (String part : properties.split(",")) {
			String[] pair = part.split("=", 2);
			if (pair.length == 2) {
				state = with(state, block, pair[0], pair[1]);
			}
		}
		return state;
	}

	/** The block id of a Sponge palette key, without its properties. */
	static String name(String raw) {
		int bracket = raw.indexOf('[');
		return bracket >= 0 ? raw.substring(0, bracket) : raw;
	}

	/** Null for a malformed id or a block this game doesn't have; air is a real block. */
	private static Block block(String name) {
		Identifier id = Identifier.tryParse(name);
		return id == null || !Registries.BLOCK.containsId(id) ? null : Registries.BLOCK.get(id);
	}

	static int number(NbtCompound compound, String key) {
		return compound.get(key) instanceof AbstractNbtNumber number ? number.intValue() : 0;
	}

	static int numberAt(NbtCompound compound, String key, int index) {
		if (compound.contains(key, NbtElement.INT_ARRAY_TYPE)) {
			int[] values = compound.getIntArray(key);
			return index < values.length ? values[index] : 0;
		}
		NbtList list = compound.getList(key, NbtElement.INT_TYPE);
		return index < list.size() ? list.getInt(index) : 0;
	}

	private static BlockState with(BlockState state, Block block, String key, String value) {
		Property<?> property = block.getStateManager().getProperty(key);
		return property == null ? state : apply(state, property, value);
	}

	private static <T extends Comparable<T>> BlockState apply(BlockState state, Property<T> property, String value) {
		return property.parse(value).map(parsed -> state.with(property, parsed)).orElse(state);
	}
}
