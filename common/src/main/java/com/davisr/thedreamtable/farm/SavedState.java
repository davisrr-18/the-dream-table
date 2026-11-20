package com.davisr.thedreamtable.farm;

import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Builds the {@link PersistentStateType} of a world-saved state whose data is an {@link NbtCompound} the state reads
 * and writes itself, in the format 1.21.1 saved (the file is still {@code data/<id>.dat} with the data under {@code data}).
 */
final class SavedState {
	private SavedState() {
	}

	/**
	 * {@code read} and {@code write} are the states' own {@code read(nbt, registries)} and {@code writeNbt(nbt, registries)}
	 * with the lookup fixed to null: none of the five states uses it (their item data is kept as plain compounds).
	 */
	static <T extends PersistentState> PersistentStateType<T> type(
			String id, Supplier<T> constructor, Function<NbtCompound, T> read, Function<T, NbtCompound> write
	) {
		// The data fix type must not be null: the game then throws while loading, swallows it, and loads an EMPTY
		// state that the next save writes over the world's real one. Command storage has no fixer that touches
		// this mod's keys, so the data passes through unchanged (SavedStateFormatTest checks it).
		return new PersistentStateType<>(id, constructor, NbtCompound.CODEC.xmap(read::apply, write::apply), DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
	}
}
