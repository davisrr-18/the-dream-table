package com.davisr.thedreamtable.farm.schematic;

import com.davisr.thedreamtable.DataFixes;
import com.davisr.thedreamtable.Nbts;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.AbstractNbtNumber;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtInt;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;

/** Small readers shared by the schematic formats: block states, numbers, and coordinate arrays. */
final class NbtReading {
	private static final String[] AXES = {"x", "y", "z"};

	private NbtReading() {
	}

	/**
	 * Reads a vanilla or Litematica palette entry: {@code {Name: "...", Properties: {...}}} written at data version
	 * {@code from}, updated to this game's names first. Null when the block isn't in this game.
	 */
	static BlockState state(NbtCompound saved, int from) {
		NbtCompound entry = DataFixes.blockState(saved, from);
		Block block = block(entry.getString("Name", ""));
		if (block == null) {
			return null;
		}
		BlockState state = block.getDefaultState();
		if (!Nbts.hasCompound(entry, "Properties")) {
			return state;
		}
		NbtCompound properties = entry.getCompoundOrEmpty("Properties");
		for (String key : properties.getKeys()) {
			state = with(state, block, key, properties.getString(key, ""));
		}
		return state;
	}

	/**
	 * Reads a Sponge palette key: {@code minecraft:stairs[facing=east,half=bottom]}, written at data version {@code from}.
	 * Null when the block isn't in this game.
	 */
	static BlockState state(String raw, int from) {
		NbtCompound entry = new NbtCompound();
		entry.putString("Name", name(raw));
		int bracket = raw.indexOf('[');
		if (bracket >= 0) {
			NbtCompound properties = new NbtCompound();
			String list = raw.substring(bracket + 1, raw.endsWith("]") ? raw.length() - 1 : raw.length());
			for (String part : list.split(",")) {
				String[] pair = part.split("=", 2);
				if (pair.length == 2) {
					properties.putString(pair[0], pair[1]);
				}
			}
			if (!properties.isEmpty()) {
				entry.put("Properties", properties);
			}
		}
		return state(entry, from);
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

	/** One coordinate of a position or size stored as an int array, an int list, or Litematica's {@code {x, y, z}} compound. */
	static int numberAt(NbtCompound compound, String key, int index) {
		if (Nbts.hasCompound(compound, key)) {
			return index < 3 ? number(compound.getCompoundOrEmpty(key), AXES[index]) : 0;
		}
		if (Nbts.hasIntArray(compound, key)) {
			int[] values = Nbts.ints(compound, key);
			return index < values.length ? values[index] : 0;
		}
		NbtList list = Nbts.list(compound, key, NbtInt.class);
		return index < list.size() ? list.getInt(index, 0) : 0;
	}

	private static BlockState with(BlockState state, Block block, String key, String value) {
		Property<?> property = block.getStateManager().getProperty(key);
		return property == null ? state : apply(state, property, value);
	}

	private static <T extends Comparable<T>> BlockState apply(BlockState state, Property<T> property, String value) {
		return property.parse(value).map(parsed -> state.with(property, parsed)).orElse(state);
	}
}
