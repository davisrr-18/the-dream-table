package com.davisr.thedreamtable.block.entity;

import com.davisr.thedreamtable.farm.BuildPace;
import com.davisr.thedreamtable.farm.FarmNames;
import com.davisr.thedreamtable.farm.schematic.SchematicReader;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import org.jetbrains.annotations.Nullable;

import java.util.BitSet;
import java.util.UUID;

import static com.davisr.thedreamtable.block.entity.DreamTableBlockEntity.*;

/**
 * Where the build is: its phase, the next step, the tick timer, who started it, and how fast it goes.
 * It also records which steps the table placed itself and which of those used a delivered item,
 * so a discard only takes apart and refunds its own work. A stopped build keeps its {@link BuildIssue}; a repair keeps
 * count of the blocks it left alone because the player had changed them.
 */
final class BuildProgress {
	private final BitSet placed = new BitSet();
	private final BitSet paid = new BitSet();
	private String phase = PHASE_IDLE;
	private int index;
	private float credit;
	private UUID owner;
	private int pace = BuildPace.DEFAULT;
	private long signature;
	private @Nullable BuildIssue issue;
	private boolean repair;
	private @Nullable RepairContext repairContext;
	private @Nullable UUID repairInstanceId;
	private @Nullable String repairCustomName;
	private int kept;
	/** Whether the finished farm of this DONE session already has its record. Saved, so a reload never records it again. */
	private boolean finishedRecorded;
	/** A DONE session saved before the flag existed: whether it was recorded is unknown until the table first ticks it. Not saved. */
	private boolean legacyDone;
	/** Whether this run already told its player that a returned item was dropped. Not persisted. */
	private boolean droppedNotified;
	/**
	 * Set when a repair ended while its actor was away: the lang key of the message they get on return, when their delivered
	 * materials are handed back. Empty when nothing waits. Saved, so a restart can't strand the materials.
	 */
	private String returnNotice = "";

	/** False when the farm file no longer matches the one this build started with. An unknown signature, from an older save, matches. */
	boolean matches(long current) {
		return signature == 0 || signature == current;
	}

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

	BitSet placedSteps() {
		return (BitSet) placed.clone();
	}

	BitSet paidSteps() {
		return (BitSet) paid.clone();
	}

	long signature() {
		return signature;
	}

	@Nullable BuildIssue issue() {
		return issue;
	}

	void setIssue(@Nullable BuildIssue value) {
		issue = value;
	}

	boolean repair() {
		return repair;
	}

	boolean finishedRecorded() {
		return finishedRecorded;
	}

	void setFinishedRecorded(boolean value) {
		finishedRecorded = value;
		legacyDone = false;
	}

	boolean legacyDone() {
		return legacyDone;
	}

	void settleLegacy() {
		legacyDone = false;
	}

	void startRepair(UUID instanceId, @Nullable String customName) {
		repair = true;
		repairContext = null;
		repairInstanceId = instanceId;
		repairCustomName = customName;
		kept = 0;
	}

	void startRepair(RepairContext context) {
		repair = true;
		repairContext = context;
		repairInstanceId = context.farmKey().instanceId();
		repairCustomName = null;
		kept = 0;
	}

	@Nullable RepairContext repairContext() {
		return repairContext;
	}

	@Nullable UUID repairInstanceId() {
		return repairInstanceId;
	}

	@Nullable String repairCustomName() {
		return repairCustomName;
	}


	int kept() {
		return kept;
	}

	void keep() {
		kept++;
	}

	String returnNotice() {
		return returnNotice;
	}

	void setReturnNotice(String key) {
		returnNotice = key;
	}

	/** True only for the first dropped handover in this build or repair. */
	boolean shouldNotifyDropped(boolean dropped) {
		if (!dropped || droppedNotified) {
			return false;
		}
		droppedNotified = true;
		return true;
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

	UUID actorUuid() {
		return owner;
	}

	int pace() {
		return pace;
	}

	void setPace(int value) {
		pace = BuildPace.valid(value);
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

	void start(UUID player, boolean fromScratch, long currentSignature) {
		owner = player;
		if (fromScratch) {
			index = 0;
			placed.clear();
			paid.clear();
			repair = false;
			repairContext = null;
			repairInstanceId = null;
			repairCustomName = null;
			kept = 0;
			droppedNotified = false;
			finishedRecorded = false;
			legacyDone = false;
			returnNotice = "";
		}
		issue = null;
		if (fromScratch || signature == 0) {
			signature = currentSignature;
		}
		phase = PHASE_BUILDING;
		credit = 1;
	}

	void stop(String nextPhase) {
		phase = nextPhase;
	}

	void advance() {
		index++;
	}

	/**
	 * Counts one tick and returns how many blocks are due now. The rate is spread over the ticks of each second,
	 * so 1 block per second places one block every 20 ticks and 40 places two every tick, instead of all at once.
	 */
	int due(int blocksPerSecond) {
		credit += (float) blocksPerSecond / BuildPace.TICKS_PER_SECOND;
		int blocks = (int) credit;
		credit -= blocks;
		return blocks;
	}

	/** Back to idle for a new or discarded farm; the speed is a table setting and stays. */
	void reset(boolean forgetOwner) {
		phase = PHASE_IDLE;
		index = 0;
		credit = 0;
		placed.clear();
		paid.clear();
		signature = 0;
		issue = null;
		repair = false;
		repairContext = null;
		repairInstanceId = null;
		repairCustomName = null;
		kept = 0;
		droppedNotified = false;
		finishedRecorded = false;
		legacyDone = false;
		returnNotice = "";
		if (forgetOwner) {
			owner = null;
		}
	}

	/** A saved step number is kept within what any farm can have, so a corrupt value can't size the legacy step set into gigabytes. */
	static int clampIndex(int saved) {
		return Math.max(0, Math.min(SchematicReader.MAX_BLOCKS, saved));
	}

	void write(NbtCompound nbt) {
		nbt.putString("Phase", phase);
		nbt.putInt("BuildIndex", index);
		nbt.putFloat("Credit", credit);
		nbt.putInt("BlocksPerSecond", pace);
		if (owner != null) {
			nbt.putUuid("Owner", owner);
		}
		nbt.putLong("FarmSignature", signature);
		nbt.putLongArray("PlacedSteps", placed.toLongArray());
		nbt.putLongArray("PaidSteps", paid.toLongArray());
		nbt.putBoolean("Repair", repair);
		if (repairContext != null) {
			repairContext.write(nbt);
		}
		if (repairInstanceId != null) {
			nbt.putUuid("RepairInstanceId", repairInstanceId);
		}
		if (repairCustomName != null) {
			nbt.putString("RepairCustomName", repairCustomName);
		}
		nbt.putInt("Kept", kept);
		nbt.putBoolean("FinishedRecorded", finishedRecorded);
		if (!returnNotice.isEmpty()) {
			nbt.putString("ReturnNotice", returnNotice);
		}
		if (issue != null) {
			issue.write(nbt);
		}
	}

	/** What clients need to draw the menu, hologram and clipboard, and why the build stopped. The step records, timer and signature stay on the server. */
	void writeForClient(NbtCompound nbt) {
		nbt.putString("Phase", phase);
		nbt.putInt("BuildIndex", index);
		nbt.putInt("BlocksPerSecond", pace);
		nbt.putBoolean("Repair", repair);
		if (issue != null) {
			issue.write(nbt);
		}
	}

	void read(NbtCompound nbt) {
		String stored = nbt.getString("Phase");
		phase = stored.isEmpty() ? PHASE_IDLE : stored;
		index = clampIndex(nbt.getInt("BuildIndex"));
		credit = Math.max(0, Math.min(1, nbt.getFloat("Credit")));
		pace = BuildPace.valid(nbt.getInt("BlocksPerSecond"));
		owner = nbt.containsUuid("Owner") ? nbt.getUuid("Owner") : null;
		signature = nbt.getLong("FarmSignature");
		placed.clear();
		placed.or(BitSet.valueOf(nbt.getLongArray("PlacedSteps")));
		paid.clear();
		paid.or(BitSet.valueOf(nbt.getLongArray("PaidSteps")));
		if (is(PHASE_DONE) && !nbt.contains("PlacedSteps", NbtElement.LONG_ARRAY_TYPE)) {
			// Finished by a version that didn't record steps: its farm is taken down as a whole, like before.
			placed.set(0, index);
			paid.set(0, index);
		}
		repair = nbt.getBoolean("Repair");
		if (repair) {
			repairContext = RepairContext.read(nbt);
			repairInstanceId = repairContext != null ? repairContext.farmKey().instanceId()
					: nbt.containsUuid("RepairInstanceId") ? nbt.getUuid("RepairInstanceId") : UUID.randomUUID();
			FarmNames.Validation name = FarmNames.validate(nbt.getString("RepairCustomName"));
			repairCustomName = name.result() == FarmNames.Result.VALID ? name.name() : null;
		} else {
			repairContext = null;
			repairInstanceId = null;
			repairCustomName = null;
		}
		// "RepairGrants" and "RepairInvites" from earlier versions are not read and are dropped on the next save.
		kept = nbt.getInt("Kept");
		// A DONE session saved without the flag counts as recorded, so a farm taken down or forgotten since can't come back by
		// itself. Versions before 1.1.4-beta never recorded, though, so its first tick decides whether the farm still stands and
		// has no record ({@link SessionBuild#legacyDecision}).
		boolean flagged = nbt.contains("FinishedRecorded", NbtElement.BYTE_TYPE);
		finishedRecorded = flagged ? nbt.getBoolean("FinishedRecorded") : is(PHASE_DONE);
		legacyDone = !flagged && is(PHASE_DONE);
		returnNotice = nbt.getString("ReturnNotice");
		issue = BuildIssue.read(nbt);
	}
}
