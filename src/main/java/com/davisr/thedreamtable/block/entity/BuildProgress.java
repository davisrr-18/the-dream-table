package com.davisr.thedreamtable.block.entity;

import net.minecraft.nbt.NbtCompound;

import java.util.BitSet;
import java.util.UUID;

import static com.davisr.thedreamtable.block.entity.DreamTableBlockEntity.*;

/**
 * Where the build is: its phase, the next step, the tick timer, who started it, and how fast it goes.
 * It also records which steps the table placed itself and which of those used a delivered item,
 * so a discard only takes apart and refunds its own work.
 */
final class BuildProgress {
	private final BitSet placed = new BitSet();
	private final BitSet paid = new BitSet();
	private String phase = PHASE_IDLE;
	private int index;
	private int ticks;
	private UUID owner;
	private int blocksPerSecond = 1;

	void recordPlaced(boolean usedItem) {
		placed.set(index);
		paid.set(index, usedItem);
	}

	boolean placed(int step) {
		return placed.get(step);
	}

	boolean paid(int step) {
		return paid.get(step);
	}

	String phase() {
		return phase;
	}

	int index() {
		return index;
	}

	UUID owner() {
		return owner;
	}

	int blocksPerSecond() {
		return blocksPerSecond;
	}

	void setBlocksPerSecond(int speed) {
		blocksPerSecond = validSpeed(speed);
	}

	boolean is(String expected) {
		return expected.equals(phase);
	}

	boolean busy() {
		return is(PHASE_BUILDING) || resumable();
	}

	/** Stopped partway, by a blocked spot, a missing block, or the player; the build picks up at the same step. */
	boolean resumable() {
		return is(PHASE_BLOCKED) || is(PHASE_NEED) || is(PHASE_PAUSED);
	}

	void start(UUID player, boolean fromScratch) {
		owner = player;
		if (fromScratch) {
			index = 0;
			placed.clear();
			paid.clear();
		}
		phase = PHASE_BUILDING;
		ticks = 0;
	}

	void stop(String nextPhase) {
		phase = nextPhase;
	}

	void advance() {
		index++;
	}

	/** Counts one tick and returns true once a full second has passed. */
	boolean second() {
		ticks++;
		if (ticks < TICKS_PER_BLOCK) {
			return false;
		}
		ticks = 0;
		return true;
	}

	/** Back to idle for a new or discarded farm; the speed is a table setting and stays. */
	void reset(boolean forgetOwner) {
		phase = PHASE_IDLE;
		index = 0;
		ticks = 0;
		placed.clear();
		paid.clear();
		if (forgetOwner) {
			owner = null;
		}
	}

	void write(NbtCompound nbt) {
		nbt.putString("Phase", phase);
		nbt.putInt("BuildIndex", index);
		nbt.putInt("Ticks", ticks);
		nbt.putInt("BlocksPerSecond", blocksPerSecond);
		if (owner != null) {
			nbt.putUuid("Owner", owner);
		}
		nbt.putLongArray("PlacedSteps", placed.toLongArray());
		nbt.putLongArray("PaidSteps", paid.toLongArray());
	}

	void read(NbtCompound nbt) {
		String stored = nbt.getString("Phase");
		phase = stored.isEmpty() ? PHASE_IDLE : stored;
		index = Math.max(0, nbt.getInt("BuildIndex"));
		ticks = Math.max(0, nbt.getInt("Ticks"));
		blocksPerSecond = validSpeed(nbt.getInt("BlocksPerSecond"));
		owner = nbt.containsUuid("Owner") ? nbt.getUuid("Owner") : null;
		placed.clear();
		placed.or(BitSet.valueOf(nbt.getLongArray("PlacedSteps")));
		paid.clear();
		paid.or(BitSet.valueOf(nbt.getLongArray("PaidSteps")));
	}
}
