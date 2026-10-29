package com.davisr.thedreamtable.network;

import com.davisr.thedreamtable.platform.Platform;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Remembers each player's last answer to a read-only scan for a few ticks, so the same question asked again within that
 * time gets the same answer without scanning again. Only the latest answer per player is kept, which bounds memory by the
 * number of players. Used on the server thread only.
 *
 * @param <K> what was asked; two equal keys are the same question
 * @param <V> the answer
 */
final class RecentResults<K, V> {
	private record Entry<K, V>(K key, V value, long tick) {
	}

	private final int ttlTicks;
	private final Map<UUID, Entry<K, V>> latest = new HashMap<>();

	RecentResults(int ttlTicks) {
		this.ttlTicks = Math.max(1, ttlTicks);
	}

	/** The answer to this question if it was stored less than the time to live ago, otherwise null. */
	V get(UUID player, K key, long nowTick) {
		Entry<K, V> entry = latest.get(player);
		// A tick counter that went backwards, such as after a world switch, makes the answer stale instead of eternal.
		if (entry == null || !entry.key().equals(key) || entry.tick() > nowTick || nowTick - entry.tick() >= ttlTicks) {
			return null;
		}
		return entry.value();
	}

	void put(UUID player, K key, V value, long nowTick) {
		latest.put(player, new Entry<>(key, value, nowTick));
	}

	void forget(UUID player) {
		latest.remove(player);
	}

	/** Drops a leaving player's answer; call once from the payload's {@code register}. */
	void forgetOnDisconnect() {
		Platform.serverEvents().onPlayerDisconnect((player, server) -> server.execute(() -> forget(player.getUuid())));
	}
}
