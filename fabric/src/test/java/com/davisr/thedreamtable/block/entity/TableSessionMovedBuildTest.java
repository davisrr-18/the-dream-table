package com.davisr.thedreamtable.block.entity;

import com.davisr.thedreamtable.GameSetup;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A build kept from a broken table keeps its world on a save; a damaged world id is never read as a build that never moved. */
class TableSessionMovedBuildTest {
	private static final UUID PLAYER = UUID.fromString("10000000-0000-0000-0000-000000000001");

	@BeforeAll
	static void setUp() {
		GameSetup.bootstrap();
	}

	/** Reading and writing a saved session never reach the table (no world, no sync), so none is made: the block entity needs the platform. */
	private static TableSession session() {
		return new TableSession(null, PLAYER);
	}

	/** The save of a kept build that stands in the Nether, as {@link TableSession#write} writes it. */
	private static NbtCompound savedBuild(String world) {
		NbtCompound saved = new NbtCompound();
		saved.putString("MovedBuildDimension", world);
		TableSession read = session();
		read.read(saved, null);
		NbtCompound written = new NbtCompound();
		read.write(written, null);
		return written;
	}

	@Test
	void aMovedBuildDimensionSurvivesAWriteAndRead() {
		NbtCompound written = savedBuild("minecraft:the_nether");
		TableSession back = session();
		back.read(written, null);
		assertTrue(back.movedBuild());
		assertEquals("minecraft:the_nether", written.getString("MovedBuildDimension", ""));
	}

	/**
	 * A present but unparsable dimension reads as an unreadable session, which is kept as saved, and never as a session that never
	 * moved (that would resume it as a local build at its saved spot, and skip the world and reach checks).
	 */
	@Test
	void aMalformedMovedBuildDimensionIsNotReadAsNotMoved() {
		NbtCompound written = savedBuild("minecraft:the_nether");
		written.putString("MovedBuildDimension", "Not A World");
		assertThrows(IllegalArgumentException.class, () -> session().read(written, null));
	}

	/** Read the way the table reads its saved sessions: the damaged entry is handed back unchanged, so a later version can still read it. */
	@Test
	void aMalformedMovedBuildIsKeptUnreadableAsSaved() {
		NbtCompound damaged = savedBuild("minecraft:the_nether");
		damaged.putString("MovedBuildDimension", "Not A World");
		NbtList list = new NbtList();
		list.add(damaged);
		List<NbtCompound> unreadable = GuardedRead.forEach(list, "test session", entry -> session().read(entry, null));
		assertEquals(1, unreadable.size());
		assertEquals(damaged, unreadable.get(0));
	}
}
