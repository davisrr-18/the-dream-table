package com.davisr.thedreamtable.block.entity;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The aimed spot saved with a session: what it writes, it reads back, and an entry missing part of it is no spot. */
class AimedSpotTest {
	private static final Identifier OVERWORLD = Identifier.of("minecraft", "overworld");

	@Test
	void aSpotReadsBackAsItWasWritten() {
		AimedSpot spot = new AimedSpot(new BlockPos(-12, 64, 900), Direction.WEST, true, OVERWORLD);
		assertEquals(spot, AimedSpot.fromNbt(spot.toNbt()));
	}

	@Test
	void aNonHorizontalFacingBecomesSouth() {
		assertEquals(Direction.SOUTH, new AimedSpot(BlockPos.ORIGIN, Direction.UP, false, OVERWORLD).facing());
		assertEquals(Direction.SOUTH, new AimedSpot(BlockPos.ORIGIN, null, false, OVERWORLD).facing());
	}

	/**
	 * The rule a restored aimed spot must pass, as {@link TableSession#checkRestoredAim} applies it: the table has a farm, the spot
	 * is in its world and reach, and it is free. Each part alone is enough to drop the spot.
	 */
	@Test
	void aRestoredSpotIsKeptOnlyWhenEveryPartHolds() {
		assertTrue(TableSession.restoredAimUsable(true, true, true, true));
		assertFalse(TableSession.restoredAimUsable(false, true, true, true), "no farm to place");
		assertFalse(TableSession.restoredAimUsable(true, false, true, true), "another world");
		assertFalse(TableSession.restoredAimUsable(true, true, false, true), "out of reach");
		assertFalse(TableSession.restoredAimUsable(true, true, true, false), "no longer free");
	}

	@Test
	void anEntryMissingAnyPartIsNoSpot() {
		NbtCompound whole = new AimedSpot(new BlockPos(1, 2, 3), Direction.NORTH, false, OVERWORLD).toNbt();
		for (String key : new String[] {"X", "Y", "Z"}) {
			NbtCompound missing = whole.copy();
			missing.remove(key);
			assertNull(AimedSpot.fromNbt(missing), key);
		}
		NbtCompound badDimension = whole.copy();
		badDimension.putString("Dimension", "Not A Valid Id");
		assertNull(AimedSpot.fromNbt(badDimension));
	}
}
