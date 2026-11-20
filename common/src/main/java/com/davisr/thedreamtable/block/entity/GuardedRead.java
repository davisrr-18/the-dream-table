package com.davisr.thedreamtable.block.entity;

import com.davisr.thedreamtable.Nbts;
import com.davisr.thedreamtable.TheDreamTableMod;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Reads a saved list one entry at a time, so one malformed entry doesn't take the rest of the table's data with it.
 * An entry that fails is logged and handed back as it was saved, for the caller to write out again unchanged: a later
 * version that can read it gets it back, instead of the next save deleting it.
 */
final class GuardedRead {
	private GuardedRead() {
	}

	/** Whether {@code entry} is a saved session of a player who has a readable one now. */
	static boolean superseded(NbtCompound entry, Set<UUID> live) {
		return Nbts.containsUuid(entry, "Player") && live.contains(Nbts.getUuid(entry, "Player"));
	}

	/**
	 * Drops the unreadable entries whose player has a readable entry now, so a later version can never read the old one
	 * over the newer session. An entry whose player can't be read is kept. Returns how many were dropped.
	 */
	static int dropSuperseded(List<NbtCompound> unreadable, Set<UUID> live) {
		int dropped = 0;
		Iterator<NbtCompound> entries = unreadable.iterator();
		while (entries.hasNext()) {
			NbtCompound entry = entries.next();
			if (superseded(entry, live)) {
				TheDreamTableMod.LOGGER.warn("Dropping an unreadable saved session of {}: a newer one exists", Nbts.getUuid(entry, "Player"));
				entries.remove();
				dropped++;
			}
		}
		return dropped;
	}

	/** Runs {@code reader} on every compound of {@code list}; returns copies of the ones it threw on. */
	static List<NbtCompound> forEach(NbtList list, String what, Consumer<NbtCompound> reader) {
		List<NbtCompound> unreadable = new ArrayList<>();
		for (int index = 0; index < list.size(); index++) {
			NbtCompound entry = list.getCompoundOrEmpty(index);
			try {
				reader.accept(entry);
			} catch (RuntimeException corrupt) {
				TheDreamTableMod.LOGGER.error("Keeping unreadable {} #{} unchanged", what, index, corrupt);
				unreadable.add(entry.copy());
			}
		}
		return unreadable;
	}
}
