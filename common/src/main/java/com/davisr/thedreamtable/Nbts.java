package com.davisr.thedreamtable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.MapCodec;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIntArray;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtLongArray;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.AbstractNbtNumber;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.RegistryWrapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The 1.21.1 NBT helpers that 1.21.11 removed, with the same semantics, so saved data keeps its format: UUIDs as an
 * int array of four, typed {@code contains} checks, and item stacks as plain {id, count, components} compounds.
 * Plain scalar getters need no helper: {@code getInt(key, 0)}, {@code getString(key, "")} and
 * {@code getCompoundOrEmpty(key)} read like the old ones. A typed list does not: 1.21.1's {@code getList(key, TYPE)}
 * returned an empty list when the elements had another type, and {@code getListOrEmpty} keeps them, so read a list
 * through {@link #list} wherever the element type matters.
 */
public final class Nbts {
	/** Any NBT element, untouched. Lets a view hand back a saved list exactly as it was written, even a malformed one. */
	public static final Codec<NbtElement> ELEMENT = Codec.PASSTHROUGH.xmap(
			dynamic -> dynamic.convert(NbtOps.INSTANCE).getValue(),
			element -> new Dynamic<>(NbtOps.INSTANCE, element)
	);

	/**
	 * A block entity's whole data map as one compound. Lets a block entity keep reading and writing its own 1.21.1
	 * format through a {@code ReadView}/{@code WriteView}: {@code view.read(ROOT)} and {@code view.put(ROOT, nbt)}.
	 */
	public static final MapCodec<NbtCompound> ROOT = MapCodec.assumeMapUnsafe(NbtCompound.CODEC);

	private Nbts() {
	}

	/** Writes a UUID as an int array of four (the 1.21.1 {@code putUuid} format; never the string form). */
	public static void putUuid(NbtCompound nbt, String key, UUID uuid) {
		long most = uuid.getMostSignificantBits();
		long least = uuid.getLeastSignificantBits();
		nbt.putIntArray(key, new int[] {(int) (most >> 32), (int) most, (int) (least >> 32), (int) least});
	}

	/** Whether the key holds a readable UUID: an int array of exactly four. */
	public static boolean containsUuid(NbtCompound nbt, String key) {
		return nbt.get(key) instanceof NbtIntArray array && array.getIntArray().length == 4;
	}

	/** Reads a UUID written by {@link #putUuid}. Throws on a missing or malformed value, like the old {@code getUuid}. */
	public static UUID getUuid(NbtCompound nbt, String key) {
		if (!(nbt.get(key) instanceof NbtIntArray array) || array.getIntArray().length != 4) {
			throw new IllegalArgumentException("Expected a UUID (int array of four) at '" + key + "'");
		}
		int[] ints = array.getIntArray();
		return new UUID((long) ints[0] << 32 | (ints[1] & 0xFFFFFFFFL), (long) ints[2] << 32 | (ints[3] & 0xFFFFFFFFL));
	}

	public static boolean hasList(NbtCompound nbt, String key) {
		return nbt.get(key) instanceof NbtList;
	}

	public static boolean hasCompound(NbtCompound nbt, String key) {
		return nbt.get(key) instanceof NbtCompound;
	}

	public static boolean hasString(NbtCompound nbt, String key) {
		return nbt.get(key) instanceof NbtString;
	}

	/** Any numeric tag (byte to double), like the old {@code contains(key, NUMBER_TYPE)}. */
	public static boolean hasNumber(NbtCompound nbt, String key) {
		return nbt.get(key) instanceof AbstractNbtNumber;
	}

	public static boolean hasIntArray(NbtCompound nbt, String key) {
		return nbt.get(key) instanceof NbtIntArray;
	}

	public static boolean hasLongArray(NbtCompound nbt, String key) {
		return nbt.get(key) instanceof NbtLongArray;
	}

	/**
	 * The key's list the way 1.21.1's typed {@code getList(key, TYPE)} read it: the list when every element is a
	 * {@code type}, else an empty list. Use it on input that is not the mod's own (player schematics), so a list of
	 * the wrong tag type is skipped as before instead of being read element by element with fallbacks.
	 */
	public static NbtList list(NbtCompound nbt, String key, Class<? extends NbtElement> type) {
		NbtList list = nbt.getListOrEmpty(key);
		for (NbtElement element : list) {
			if (!type.isInstance(element)) {
				return new NbtList();
			}
		}
		return list;
	}

	/**
	 * The compounds of the key's list (empty when the key is missing). Throws when an element is not a compound, so a
	 * malformed record fails whole and is kept as it was, instead of being read with placeholders and saved altered.
	 */
	public static List<NbtCompound> compounds(NbtCompound nbt, String key) {
		NbtList list = nbt.getListOrEmpty(key);
		List<NbtCompound> compounds = new ArrayList<>(list.size());
		for (int index = 0; index < list.size(); index++) {
			compounds.add(list.getCompound(index).orElseThrow(() -> new IllegalArgumentException("'" + key + "' holds a non-compound entry")));
		}
		return compounds;
	}

	/** The strings of the key's list (empty when the key is missing); throws when an element is not a string, like {@link #compounds}. */
	public static List<String> strings(NbtCompound nbt, String key) {
		NbtList list = nbt.getListOrEmpty(key);
		List<String> strings = new ArrayList<>(list.size());
		for (int index = 0; index < list.size(); index++) {
			strings.add(list.getString(index).orElseThrow(() -> new IllegalArgumentException("'" + key + "' holds a non-string entry")));
		}
		return strings;
	}

	/** The key's long array, or an empty one: what the old {@code getLongArray} returned when it was missing. */
	public static long[] longs(NbtCompound nbt, String key) {
		return nbt.getLongArray(key).orElseGet(() -> new long[0]);
	}

	/** The key's int array, or an empty one. */
	public static int[] ints(NbtCompound nbt, String key) {
		return nbt.getIntArray(key).orElseGet(() -> new int[0]);
	}

	/** Encodes non-empty item stacks as a list of {id, count, components} compounds, the 1.21.1 inventory list format. */
	public static NbtList writeItems(List<ItemStack> stacks, RegistryWrapper.WrapperLookup registries) {
		NbtList list = new NbtList();
		for (ItemStack stack : stacks) {
			if (!stack.isEmpty()) {
				list.add(ItemStack.CODEC.encodeStart(registries.getOps(NbtOps.INSTANCE), stack).getOrThrow());
			}
		}
		return list;
	}

	/**
	 * Decodes a list written by {@link #writeItems} at data version {@code from}: each stack is first updated to the
	 * game's current format (renamed items, changed components), so a stack saved by an older game still reads. An entry
	 * that no longer parses is logged and skipped, as the old reader did.
	 */
	public static List<ItemStack> readItems(NbtList list, RegistryWrapper.WrapperLookup registries, int from) {
		List<ItemStack> stacks = new ArrayList<>();
		for (NbtElement saved : list) {
			NbtElement entry = saved instanceof NbtCompound compound ? DataFixes.itemStack(compound, from) : saved;
			ItemStack.CODEC.parse(registries.getOps(NbtOps.INSTANCE), entry)
					.resultOrPartial(error -> TheDreamTableMod.LOGGER.warn("Skipping an item that no longer reads: {}", error))
					.ifPresent(stacks::add);
		}
		return stacks;
	}
}
