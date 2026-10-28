package com.davisr.thedreamtable.farm.alert;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Bounded persisted alert state for one finished farm. */
public final class FarmAlerts {
	private static final int MAX_HISTORY = 20;
	private static final String ACTIVE = "ActiveAlerts";
	private static final String HISTORY = "AlertHistory";
	private static final String NEXT = "NextAlertEvent";
	private static final String EVENT = "Event";
	private static final String TYPE = "Type";
	private static final String QUANTITY = "Quantity";
	private static final String TIME = "Time";

	public enum Transition {
		NEW, UPDATED, RESOLVED, UNCHANGED, UNKNOWN
	}

	public record Event(long eventId, FarmAlertType type, int quantity, long time) {
	}

	private final Map<FarmAlertType, Event> active = new EnumMap<>(FarmAlertType.class);
	private final List<Event> history = new ArrayList<>();
	private long nextEventId = 1L;

	public Transition observe(FarmAlertType type, int quantity, long time) {
		if (type == FarmAlertType.REPAIR_COMPLETED) {
			record(type, time);
			return Transition.NEW;
		}
		if (quantity <= 0) {
			return resolve(type, time);
		}
		Event current = active.get(type);
		if (current == null) {
			active.put(type, event(type, quantity, time));
			return Transition.NEW;
		}
		if (current.quantity() == quantity) {
			return Transition.UNCHANGED;
		}
		active.put(type, new Event(current.eventId(), type, quantity, time));
		return Transition.UPDATED;
	}

	/** An unloaded or otherwise unavailable observation never clears a known problem. */
	public Transition unknown(FarmAlertType type) {
		return active.containsKey(type) ? Transition.UNKNOWN : Transition.UNCHANGED;
	}

	public Transition resolve(FarmAlertType type, long time) {
		Event removed = active.remove(type);
		if (removed == null) {
			return Transition.UNCHANGED;
		}
		history.add(new Event(removed.eventId(), removed.type(), removed.quantity(), time));
		trimHistory();
		return Transition.RESOLVED;
	}

	public void record(FarmAlertType type, long time) {
		history.add(event(type, 1, time));
		trimHistory();
	}

	public Optional<Event> active(FarmAlertType type) {
		return Optional.ofNullable(active.get(type));
	}

	public List<Event> active() {
		return active.values().stream().sorted(Comparator.comparingLong(Event::eventId)).toList();
	}

	public List<Event> history() {
		return List.copyOf(history);
	}

	public NbtCompound write(NbtCompound nbt) {
		NbtList savedActive = new NbtList();
		active().forEach(event -> savedActive.add(write(event)));
		nbt.put(ACTIVE, savedActive);
		NbtList savedHistory = new NbtList();
		history.forEach(event -> savedHistory.add(write(event)));
		nbt.put(HISTORY, savedHistory);
		nbt.putLong(NEXT, nextEventId);
		return nbt;
	}

	public static FarmAlerts read(NbtCompound nbt) {
		FarmAlerts alerts = new FarmAlerts();
		read(nbt.getList(ACTIVE, NbtElement.COMPOUND_TYPE), alerts.active, alerts, true);
		read(nbt.getList(HISTORY, NbtElement.COMPOUND_TYPE), null, alerts, false);
		alerts.nextEventId = Math.max(nbt.getLong(NEXT), alerts.nextEventId);
		alerts.trimHistory();
		return alerts;
	}

	private static void read(NbtList saved, Map<FarmAlertType, Event> destination, FarmAlerts alerts, boolean active) {
		for (int index = 0; index < saved.size() && index < MAX_HISTORY; index++) {
			Event event = readEvent(saved.getCompound(index));
			if (event == null) {
				continue;
			}
			alerts.nextEventId = Math.max(alerts.nextEventId, event.eventId() + 1L);
			if (active) {
				destination.merge(event.type(), event, (first, second) -> first.eventId() > second.eventId() ? first : second);
			} else {
				alerts.history.add(event);
			}
		}
	}

	private Event event(FarmAlertType type, int quantity, long time) {
		return new Event(nextEventId++, type, quantity, time);
	}

	private static NbtCompound write(Event event) {
		NbtCompound saved = new NbtCompound();
		saved.putLong(EVENT, event.eventId());
		saved.putString(TYPE, event.type().name());
		saved.putInt(QUANTITY, event.quantity());
		saved.putLong(TIME, event.time());
		return saved;
	}

	private static Event readEvent(NbtCompound saved) {
		try {
			long event = saved.getLong(EVENT);
			int quantity = saved.getInt(QUANTITY);
			if (event <= 0 || quantity <= 0) {
				return null;
			}
			return new Event(event, FarmAlertType.valueOf(saved.getString(TYPE)), quantity, saved.getLong(TIME));
		} catch (IllegalArgumentException malformed) {
			return null;
		}
	}

	private void trimHistory() {
		history.sort(Comparator.comparingLong(Event::eventId).reversed());
		while (history.size() > MAX_HISTORY) {
			history.remove(history.size() - 1);
		}
	}
}
