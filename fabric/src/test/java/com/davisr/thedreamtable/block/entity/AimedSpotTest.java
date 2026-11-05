package com.davisr.thedreamtable.block.entity;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
