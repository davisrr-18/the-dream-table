package com.davisr.thedreamtable;

import com.mojang.datafixers.DSL;
import com.mojang.serialization.Dynamic;
import net.minecraft.SharedConstants;
import net.minecraft.datafixer.Schemas;
import net.minecraft.datafixer.TypeReferences;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtString;

/**
 * Runs the game's own data fixers over the block, item and entity ids that the mod keeps outside the game's saves:
 * schematics, and the designs it stores with finished farms. Vanilla only fixes what it saves itself, so without
 * this a renamed id (1.21.9 turned {@code minecraft:chain} into {@code minecraft:iron_chain}) would read as a block
 * this game doesn't have. A value already in the current naming passes through unchanged.
 */
public final class DataFixes {
	/** The data version of 1.21.1, the oldest data the mod assumes when a file or stored design names none. */
	public static final int ASSUMED = 3955;

	private DataFixes() {
	}

	/** The data version of the running game. */
	public static int current() {
		return SharedConstants.getGameVersion().dataVersion().id();
	}

	/** A palette entry {@code {Name, Properties}} written at data version {@code from}, in the current game's names. */
	public static NbtCompound blockState(NbtCompound entry, int from) {
		return fix(TypeReferences.BLOCK_STATE, entry, from) instanceof NbtCompound fixed ? fixed : entry;
	}

	/** An item stack {@code {id, count, components}} written at data version {@code from}, in the current game's format. */
	public static NbtCompound itemStack(NbtCompound stack, int from) {
		return fix(TypeReferences.ITEM_STACK, stack, from) instanceof NbtCompound fixed ? fixed : stack;
	}

	/** An item id written at data version {@code from}, in the current game's names. */
	public static String itemName(String id, int from) {
		return name(TypeReferences.ITEM_NAME, id, from);
	}

	/** An entity id written at data version {@code from}, in the current game's names. */
	public static String entityName(String id, int from) {
		return name(TypeReferences.ENTITY_NAME, id, from);
	}

	private static String name(DSL.TypeReference type, String id, int from) {
		return fix(type, NbtString.of(id), from).asString().orElse(id);
	}

	private static NbtElement fix(DSL.TypeReference type, NbtElement value, int from) {
		int current = current();
		if (from >= current) {
			return value;
		}
		try {
			return Schemas.getFixer().update(type, new Dynamic<>(NbtOps.INSTANCE, value.copy()), from, current).getValue();
		} catch (RuntimeException unfixable) {
			// An entry the fixers can't read is passed on as it was; the reader then treats it as an unknown id.
			TheDreamTableMod.LOGGER.warn("Could not update {} from data version {}", value, from, unfixable);
			return value;
		}
	}
}
