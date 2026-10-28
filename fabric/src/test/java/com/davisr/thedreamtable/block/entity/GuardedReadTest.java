package com.davisr.thedreamtable.block.entity;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuardedReadTest {
	private static NbtCompound entry(int id) {
		NbtCompound nbt = new NbtCompound();
		nbt.putInt("Id", id);
		return nbt;
	}

	@Test
	void oneMalformedEntryDoesNotStopTheOthersAndComesBackUnchanged() {
		NbtList list = new NbtList();
		list.add(entry(1));
		list.add(entry(2));
		list.add(entry(3));
		List<Integer> read = new ArrayList<>();

		List<NbtCompound> unreadable = GuardedRead.forEach(list, "test entry", nbt -> {
			if (nbt.getInt("Id") == 2) {
				throw new IllegalStateException("malformed");
			}
			read.add(nbt.getInt("Id"));
		});

		assertEquals(List.of(1, 3), read);
		assertEquals(1, unreadable.size());
		assertEquals(entry(2), unreadable.get(0));
		assertNotSame(list.getCompound(1), unreadable.get(0));
	}

	@Test
	void aCleanListReportsNothing() {
		NbtList list = new NbtList();
		list.add(entry(1));
		assertTrue(GuardedRead.forEach(list, "test entry", nbt -> {
		}).isEmpty());
	}

	@Test
	void anUnreadableSessionOfAPlayerWithALiveOneIsDroppedButOtherEntriesAreKept() {
		UUID live = UUID.nameUUIDFromBytes("live".getBytes());
		UUID other = UUID.nameUUIDFromBytes("other".getBytes());
		NbtCompound superseded = new NbtCompound();
		superseded.putUuid("Player", live);
		NbtCompound alone = new NbtCompound();
		alone.putUuid("Player", other);
		NbtCompound noPlayer = new NbtCompound();
		noPlayer.putString("Phase", "building");
		List<NbtCompound> unreadable = new ArrayList<>(List.of(superseded, alone, noPlayer));

		assertEquals(1, GuardedRead.dropSuperseded(unreadable, Set.of(live)));

		assertEquals(List.of(alone, noPlayer), unreadable);
	}

	@Test
	void aSessionStartedAfterTheLoadSupersedesTheUnreadableOneAtTheNextSave() {
		UUID player = UUID.nameUUIDFromBytes("player".getBytes());
		NbtCompound raw = new NbtCompound();
		raw.putUuid("Player", player);
		List<NbtCompound> unreadable = new ArrayList<>(List.of(raw));

		// The player has no session worth saving yet: the raw one is written back as it was.
		assertEquals(0, GuardedRead.dropSuperseded(unreadable, Set.of()));
		assertEquals(1, unreadable.size());

		// Once they have one, saving writes only it, and the old one never comes back.
		assertTrue(GuardedRead.superseded(raw, Set.of(player)));
		assertEquals(1, GuardedRead.dropSuperseded(unreadable, Set.of(player)));
		assertEquals(0, GuardedRead.dropSuperseded(unreadable, Set.of()));
		assertTrue(unreadable.isEmpty());
	}
}
