package com.davisr.thedreamtable.farm;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Projects kept from broken tables come back to their own player, oldest first, bounded, and saved as they were. */
class ParkedProjectsTest {
	private static final UUID PLAYER = UUID.fromString("10000000-0000-0000-0000-000000000001");
	private static final UUID OTHER = UUID.fromString("20000000-0000-0000-0000-000000000002");

	private static NbtCompound project(String farm) {
		NbtCompound nbt = new NbtCompound();
		nbt.putString("SelectedFarm", farm);
		return nbt;
	}

	private static String take(ParkedProjects parked, UUID player) {
		List<String> taken = new ArrayList<>();
		parked.restore(player, saved -> taken.add(saved.getString("SelectedFarm")));
		return taken.isEmpty() ? null : taken.get(0);
	}

	@Test
	void projectsComeBackOldestFirstAndOnlyOnce() {
		ParkedProjects parked = new ParkedProjects();
		assertTrue(parked.park(PLAYER, project("first")));
		assertTrue(parked.park(PLAYER, project("second")));
		assertEquals("first", take(parked, PLAYER));
		assertEquals("second", take(parked, PLAYER));
		assertEquals(null, take(parked, PLAYER));
		assertEquals(0, parked.count(PLAYER));
	}

	@Test
	void eachPlayerGetsOnlyTheirOwn() {
		ParkedProjects parked = new ParkedProjects();
		parked.park(OTHER, project("theirs"));
		assertFalse(parked.restore(PLAYER, saved -> true));
		assertEquals(1, parked.count(OTHER));
	}

	@Test
	void aRefusedProjectStaysAsSavedAndTheNextIsOffered() {
		ParkedProjects parked = new ParkedProjects();
		parked.park(PLAYER, project("unreadable"));
		parked.park(PLAYER, project("good"));
		List<String> offered = new ArrayList<>();
		assertTrue(parked.restore(PLAYER, saved -> {
			offered.add(saved.getString("SelectedFarm"));
			return !saved.getString("SelectedFarm").equals("unreadable");
		}));
		assertEquals(List.of("unreadable", "good"), offered);
		assertEquals(1, parked.count(PLAYER));
		assertEquals("unreadable", take(parked, PLAYER));
	}

	@Test
	void theTakerCannotChangeWhatIsKept() {
		ParkedProjects parked = new ParkedProjects();
		NbtCompound original = project("kept");
		parked.park(PLAYER, original);
		original.putString("SelectedFarm", "changed after parking");
		parked.restore(PLAYER, saved -> {
			saved.putString("SelectedFarm", "changed by a refusing taker");
			return false;
		});
		assertEquals("kept", take(parked, PLAYER));
	}

	@Test
	void theLimitRefusesInsteadOfLosingWhatIsKept() {
		ParkedProjects parked = new ParkedProjects();
		for (int index = 0; index < ParkedProjects.MAX_PER_PLAYER; index++) {
			assertTrue(parked.park(PLAYER, project("farm_" + index)));
		}
		assertFalse(parked.canPark(PLAYER));
		assertFalse(parked.park(PLAYER, project("one_more")));
		assertEquals(ParkedProjects.MAX_PER_PLAYER, parked.count(PLAYER));
		assertTrue(parked.canPark(OTHER));
	}

	@Test
	void savedProjectsReadBackUnchanged() {
		ParkedProjects parked = new ParkedProjects();
		NbtCompound full = project("farm");
		full.putInt("BuildIndex", 42);
		full.putString("MovedBuildDimension", "minecraft:the_nether");
		full.putString("SomethingALaterVersionWrites", "kept");
		parked.park(PLAYER, full);
		parked.park(OTHER, project("theirs"));
		ParkedProjects read = ParkedProjects.read(parked.writeNbt(new NbtCompound(), null), null);
		assertEquals(1, read.count(PLAYER));
		assertEquals(1, read.count(OTHER));
		List<NbtCompound> back = new ArrayList<>();
		read.restore(PLAYER, back::add);
		assertEquals(full, back.get(0));
	}

	@Test
	void aCorruptFileStaysBounded() {
		NbtCompound players = new NbtCompound();
		NbtList many = new NbtList();
		for (int index = 0; index < ParkedProjects.MAX_PER_PLAYER + 5; index++) {
			many.add(project("farm_" + index));
		}
		players.put(PLAYER.toString(), many);
		players.put("not-a-uuid", new NbtList());
		players.put(OTHER.toString(), new NbtList());
		NbtCompound nbt = new NbtCompound();
		nbt.put("Players", players);
		ParkedProjects read = ParkedProjects.read(nbt, null);
		assertEquals(ParkedProjects.MAX_PER_PLAYER, read.count(PLAYER));
		assertEquals(0, read.count(OTHER));
		assertTrue(read.canPark(OTHER));
	}
}
