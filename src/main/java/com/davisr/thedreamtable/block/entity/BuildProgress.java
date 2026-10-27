package com.davisr.thedreamtable.block.entity;

import com.davisr.thedreamtable.farm.BuildPace;
import com.davisr.thedreamtable.farm.FarmGrant;
import com.davisr.thedreamtable.farm.FarmInvite;
import com.davisr.thedreamtable.farm.FarmNames;
import net.minecraft.nbt.NbtCompound;
import org.jetbrains.annotations.Nullable;

import java.util.BitSet;
import java.util.Map;
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
	private @Nullable UUID repairInstanceId;
	private @Nullable String repairCustomName;
	private Map<UUID, FarmGrant> repairGrants = Map.of();
	private Map<UUID, FarmInvite> repairInvites = Map.of();
	private int kept;
	/** Whether this run already told its player that a returned item was dropped. Not persisted. */
	private boolean droppedNotified;

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

	void startRepair(UUID instanceId, @Nullable String customName, Map<UUID, FarmGrant> grants, Map<UUID, FarmInvite> invites) {
		repair = true;
		repairInstanceId = instanceId;
		repairCustomName = customName;
		repairGrants = Map.copyOf(grants);
		repairInvites = Map.copyOf(invites);
		kept = 0;
	}

	@Nullable UUID repairInstanceId() {
		return repairInstanceId;
	}

	@Nullable String repairCustomName() {
		return repairCustomName;
	}

	Map<UUID, FarmGrant> repairGrants() {
		return repairGrants;
	}

	Map<UUID, FarmInvite> repairInvites() {
		return repairInvites;
	}

	int kept() {
		return kept;
	}

	void keep() {
		kept++;
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
			repairInstanceId = null;
			repairCustomName = null;
			repairGrants = Map.of();
			repairInvites = Map.of();
			kept = 0;
			droppedNotified = false;
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
		repairInstanceId = null;
		repairCustomName = null;
		repairGrants = Map.of();
		repairInvites = Map.of();
		kept = 0;
		droppedNotified = false;
		if (forgetOwner) {
			owner = null;
		}
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
		if (repairInstanceId != null) {
			nbt.putUuid("RepairInstanceId", repairInstanceId);
		}
		if (repairCustomName != null) {
			nbt.putString("RepairCustomName", repairCustomName);
		}
		FarmGrant.writeAll(nbt, "RepairGrants", repairGrants);
		FarmInvite.writeAll(nbt, "RepairInvites", repairInvites);
		nbt.putInt("Kept", kept);
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
		index = Math.max(0, nbt.getInt("BuildIndex"));
		credit = Math.max(0, Math.min(1, nbt.getFloat("Credit")));
		pace = BuildPace.valid(nbt.getInt("BlocksPerSecond"));
		owner = nbt.containsUuid("Owner") ? nbt.getUuid("Owner") : null;
		signature = nbt.getLong("FarmSignature");
		placed.clear();
		placed.or(BitSet.valueOf(nbt.getLongArray("PlacedSteps")));
		paid.clear();
		paid.or(BitSet.valueOf(nbt.getLongArray("PaidSteps")));
		repair = nbt.getBoolean("Repair");
		if (repair) {
			repairInstanceId = nbt.containsUuid("RepairInstanceId") ? nbt.getUuid("RepairInstanceId") : UUID.randomUUID();
			FarmNames.Validation name = FarmNames.validate(nbt.getString("RepairCustomName"));
			repairCustomName = name.result() == FarmNames.Result.VALID ? name.name() : null;
			repairGrants = FarmGrant.readAll(nbt, "RepairGrants");
			repairInvites = FarmInvite.readAll(nbt, "RepairInvites");
		} else {
			repairInstanceId = null;
			repairCustomName = null;
			repairGrants = Map.of();
			repairInvites = Map.of();
		}
		kept = nbt.getInt("Kept");
		issue = BuildIssue.read(nbt);
	}
}
