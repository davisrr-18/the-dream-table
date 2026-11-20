package com.davisr.thedreamtable.farm;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.util.math.ChunkPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** When a chunk's ticket is renewed: the first request of a loaded chunk is due at once, and a renewed one waits RENEW_TICKS. */
class ChunkKeeperTest {
	private static final long CHUNK = new ChunkPos(0, 0).toLong();

	@Test
	void aLoadedChunkNeverRenewedIsDueAtOnce() {
		long now = 1000L;
		long last = ChunkKeeper.lastRenewed(new Long2LongOpenHashMap(), CHUNK, now);

		assertTrue(ChunkKeeper.renewDue(now, last, true));
	}

	@Test
	void aChunkNeverRenewedEarlyInTheWorldIsDueAtOnce() {
		long now = 0L;
		long last = ChunkKeeper.lastRenewed(new Long2LongOpenHashMap(), CHUNK, now);

		assertTrue(ChunkKeeper.renewDue(now, last, true));
	}

	@Test
	void aChunkRenewedRecentlyAndStillLoadedIsNotDue() {
		long now = 1000L;
		Long2LongOpenHashMap renewed = new Long2LongOpenHashMap();
		renewed.put(CHUNK, now - 19L);

		assertFalse(ChunkKeeper.renewDue(now, ChunkKeeper.lastRenewed(renewed, CHUNK, now), true));
	}

	@Test
	void aChunkRenewedExactlyRenewTicksAgoIsDue() {
		long now = 1000L;
		Long2LongOpenHashMap renewed = new Long2LongOpenHashMap();
		renewed.put(CHUNK, now - 20L);

		assertTrue(ChunkKeeper.renewDue(now, ChunkKeeper.lastRenewed(renewed, CHUNK, now), true));
	}

	@Test
	void anUnloadedChunkIsAlwaysDue() {
		long now = 1000L;
		Long2LongOpenHashMap renewed = new Long2LongOpenHashMap();
		renewed.put(CHUNK, now);

		assertTrue(ChunkKeeper.renewDue(now, ChunkKeeper.lastRenewed(renewed, CHUNK, now), false));
	}
}
