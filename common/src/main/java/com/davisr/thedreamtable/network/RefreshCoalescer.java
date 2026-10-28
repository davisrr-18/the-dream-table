package com.davisr.thedreamtable.network;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Bounds how often one player's expensive list refresh is built without losing any: the first request goes out at once,
 * requests that arrive within the interval after it are merged into a single pending refresh that {@link #flush}
 * releases when the interval has passed. Several mutations in a burst, or a third party being refreshed by someone else's
 * actions, therefore cost at most one scan per interval for that player. Used on the server thread only.
 */
final class RefreshCoalescer {
	/** Half a second: a person never notices it, and it caps payload-driven refreshes at two scans a second per player. */
	static final int DEFAULT_INTERVAL_TICKS = 10;

	private final int interval;
	private final Map<UUID, Long> lastSent = new HashMap<>();
	private final Set<UUID> pending = new LinkedHashSet<>();

	RefreshCoalescer() {
		this(DEFAULT_INTERVAL_TICKS);
	}

	RefreshCoalescer(int intervalTicks) {
		this.interval = Math.max(1, intervalTicks);
	}

	/** True when the refresh should be sent now; otherwise the player is marked pending and {@link #flush} returns them later. */
	boolean request(UUID player, long nowTick) {
		if (due(player, nowTick)) {
			lastSent.put(player, nowTick);
			pending.remove(player);
			return true;
		}
		pending.add(player);
		return false;
	}

	/** The players whose merged refresh is due now; each is marked as sent. Cheap when nothing is pending. */
	List<UUID> flush(long nowTick) {
		if (pending.isEmpty()) {
			return List.of();
		}
		List<UUID> ready = new ArrayList<>();
		for (UUID player : pending) {
			if (due(player, nowTick)) {
				ready.add(player);
			}
		}
		for (UUID player : ready) {
			pending.remove(player);
			lastSent.put(player, nowTick);
		}
		return ready;
	}

	void forget(UUID player) {
		lastSent.remove(player);
		pending.remove(player);
	}

	/** A tick counter that went backwards, such as after a world switch, counts as elapsed instead of delaying forever. */
	private boolean due(UUID player, long nowTick) {
		Long last = lastSent.get(player);
		return last == null || last > nowTick || nowTick - last >= interval;
	}
}
