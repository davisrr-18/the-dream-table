package com.davisr.thedreamtable.farm;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import org.jetbrains.annotations.Nullable;

import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Who paid the paid steps of a finished farm when that was not its owner, for example an Operator who repaired it. Only
 * those exceptions are kept, so a farm nobody else paid for saves exactly what it did before; a step without an entry was
 * paid by the owner. Take Down refunds each paid step to its payer.
 */
public final class StepPayers {
	public static final StepPayers NONE = new StepPayers(Map.of());
	private static final int MAX_PAYERS = 64;

	private final Map<UUID, BitSet> steps;

	private StepPayers(Map<UUID, BitSet> steps) {
		this.steps = steps;
	}

	public boolean isEmpty() {
		return steps.isEmpty();
	}

	/** The player who paid this step if it was not the owner, or null when the owner did. */
	public @Nullable UUID payerOf(int step) {
		for (Map.Entry<UUID, BitSet> entry : steps.entrySet()) {
			if (entry.getValue().get(step)) {
				return entry.getKey();
			}
		}
		return null;
	}

	/** The player a step's refund goes to: its recorded payer, or {@code owner}. */
	public UUID payerOf(int step, UUID owner) {
		UUID payer = payerOf(step);
		return payer != null ? payer : owner;
	}

	/**
	 * What the payers are after {@code actor} placed {@code placed} (and paid for {@code paid} of it) on top of this record.
	 * A step placed again takes the new payment: it is the actor's when they paid and are not the owner, and the owner's
	 * (no entry) otherwise. Steps not placed this time keep their payer.
	 */
	public StepPayers afterPlacing(UUID actor, UUID owner, BitSet placed, BitSet paid) {
		Map<UUID, BitSet> next = copy(steps);
		for (BitSet set : next.values()) {
			set.andNot(placed);
		}
		if (!actor.equals(owner)) {
			BitSet actorSteps = (BitSet) placed.clone();
			actorSteps.and(paid);
			if (!actorSteps.isEmpty()) {
				next.computeIfAbsent(actor, ignored -> new BitSet()).or(actorSteps);
			}
		}
		return of(next);
	}

	/** Only the steps in {@code kept}; what a partly finished Take Down leaves on record. */
	public StepPayers restrictedTo(BitSet kept) {
		Map<UUID, BitSet> next = copy(steps);
		next.values().forEach(set -> set.and(kept));
		return of(next);
	}

	/** The paid steps grouped by who gets the refund, in step order: the recorded payers, and everything else to {@code owner}. */
	public Map<UUID, BitSet> split(BitSet refunded, UUID owner) {
		Map<UUID, BitSet> result = new LinkedHashMap<>();
		BitSet rest = (BitSet) refunded.clone();
		for (Map.Entry<UUID, BitSet> entry : steps.entrySet()) {
			BitSet theirs = (BitSet) entry.getValue().clone();
			theirs.and(refunded);
			if (!theirs.isEmpty()) {
				result.merge(entry.getKey(), theirs, (first, second) -> {
					first.or(second);
					return first;
				});
				rest.andNot(theirs);
			}
		}
		if (!rest.isEmpty()) {
			result.merge(owner, rest, (first, second) -> {
				first.or(second);
				return first;
			});
		}
		return result;
	}

	public void write(NbtCompound nbt, String key) {
		if (steps.isEmpty()) {
			return;
		}
		NbtList list = new NbtList();
		for (Map.Entry<UUID, BitSet> entry : steps.entrySet()) {
			NbtCompound item = new NbtCompound();
			item.putUuid("Player", entry.getKey());
			item.putLongArray("Steps", entry.getValue().toLongArray());
			list.add(item);
		}
		nbt.put(key, list);
	}

	/** The payers saved under {@code key}; absent or malformed entries read as none, so the owner is refunded as before. */
	public static StepPayers read(NbtCompound nbt, String key) {
		if (!nbt.contains(key, NbtElement.LIST_TYPE)) {
			return NONE;
		}
		NbtList list = nbt.getList(key, NbtElement.COMPOUND_TYPE);
		Map<UUID, BitSet> read = new LinkedHashMap<>();
		for (int index = 0; index < list.size() && read.size() < MAX_PAYERS; index++) {
			NbtCompound item = list.getCompound(index);
			if (item.containsUuid("Player") && item.contains("Steps", NbtElement.LONG_ARRAY_TYPE)) {
				read.computeIfAbsent(item.getUuid("Player"), ignored -> new BitSet()).or(BitSet.valueOf(item.getLongArray("Steps")));
			}
		}
		return of(read);
	}

	private static StepPayers of(Map<UUID, BitSet> map) {
		map.values().removeIf(BitSet::isEmpty);
		return map.isEmpty() ? NONE : new StepPayers(map);
	}

	private static Map<UUID, BitSet> copy(Map<UUID, BitSet> source) {
		Map<UUID, BitSet> copy = new LinkedHashMap<>();
		source.forEach((player, set) -> copy.put(player, (BitSet) set.clone()));
		return copy;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof StepPayers payers && steps.equals(payers.steps);
	}

	@Override
	public int hashCode() {
		return steps.hashCode();
	}
}
