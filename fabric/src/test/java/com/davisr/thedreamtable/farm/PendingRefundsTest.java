package com.davisr.thedreamtable.farm;

import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Refunds owed to players who were offline are kept, bounded, and saved. */
class PendingRefundsTest {
	private static final UUID PLAYER = UUID.fromString("10000000-0000-0000-0000-000000000001");

	@Test
	void amountsAddUpPerItem() {
		PendingRefunds refunds = new PendingRefunds();
		assertTrue(refunds.add(PLAYER, "minecraft:oak_log", 5));
		assertTrue(refunds.add(PLAYER, "minecraft:oak_log", 7));
		assertTrue(refunds.add(PLAYER, "minecraft:dirt", 1));
		assertEquals(Map.of("minecraft:oak_log", 12, "minecraft:dirt", 1), refunds.owedTo(PLAYER));
	}

	@Test
	void anAmountNeverOverflows() {
		PendingRefunds refunds = new PendingRefunds();
		refunds.add(PLAYER, "minecraft:dirt", Integer.MAX_VALUE);
		refunds.add(PLAYER, "minecraft:dirt", 10);
		assertEquals(Integer.MAX_VALUE, refunds.owedTo(PLAYER).get("minecraft:dirt"));
	}

	@Test
	void theLimitsRefuseInsteadOfLosingWhatIsKept() {
		PendingRefunds refunds = new PendingRefunds();
		for (int kind = 0; kind < PendingRefunds.MAX_KINDS_PER_PLAYER; kind++) {
			assertTrue(refunds.add(PLAYER, "test:item_" + kind, 1));
		}
		assertFalse(refunds.add(PLAYER, "test:one_more", 1));
		assertTrue(refunds.add(PLAYER, "test:item_0", 4));
		assertEquals(5, refunds.owedTo(PLAYER).get("test:item_0"));
		assertFalse(refunds.owedTo(PLAYER).containsKey("test:one_more"));
	}

	@Test
	void nothingIsKeptForAZeroAmount() {
		PendingRefunds refunds = new PendingRefunds();
		assertTrue(refunds.add(PLAYER, "minecraft:dirt", 0));
		assertTrue(refunds.owedTo(PLAYER).isEmpty());
	}

	@Test
	void whatIsOwedSurvivesASaveAndMalformedEntriesAreSkipped() {
		PendingRefunds refunds = new PendingRefunds();
		refunds.add(PLAYER, "minecraft:oak_log", 12);
		NbtCompound saved = refunds.writeNbt(new NbtCompound());
		NbtCompound players = saved.getCompound("Players");
		players.put("not-a-uuid", new NbtCompound());
		players.getCompound(PLAYER.toString()).putString("junk", "text");
		players.getCompound(PLAYER.toString()).putInt("minecraft:negative", -3);

		PendingRefunds restored = PendingRefunds.read(saved, null);

		assertEquals(Map.of("minecraft:oak_log", 12), restored.owedTo(PLAYER));
	}
}
