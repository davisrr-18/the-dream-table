package com.davisr.thedreamtable.network;

import com.davisr.thedreamtable.platform.Platform;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A small budget of accepted requests per player in a sliding window of server ticks, so a modified client cannot make
 * the server repeat a heavy read-only scan every tick while a person's bursts of clicks, which the client has no
 * way to retry, always pass. Used on the server thread only.
 */
public final class RequestThrottle {
	/** Enough for a person's fastest clicking or a hologram being aimed around. */
	public static final int DEFAULT_BUDGET = 10;
	public static final int DEFAULT_WINDOW_TICKS = 20;

	private final int budget;
	private final int window;
	private final Map<UUID, Deque<Long>> accepted = new HashMap<>();

	public RequestThrottle() {
		this(DEFAULT_BUDGET, DEFAULT_WINDOW_TICKS);
	}

	public RequestThrottle(int budget, int windowTicks) {
		this.budget = Math.max(1, budget);
		this.window = Math.max(1, windowTicks);
	}

	/** True and remembered when this player has accepted fewer than the budget of requests within the window ending now. */
	public boolean allow(UUID player, long nowTick) {
		Deque<Long> recent = accepted.computeIfAbsent(player, key -> new ArrayDeque<>());
		// A tick counter that went backwards, such as after a world switch, forgets the older entries instead of locking the player out.
		recent.removeIf(tick -> tick > nowTick || nowTick - tick >= window);
		if (recent.size() >= budget) {
			return false;
		}
		recent.addLast(nowTick);
		return true;
	}

	public void forget(UUID player) {
		accepted.remove(player);
	}

	/** Drops a leaving player's entry; call once from the payload's {@code register}. */
	public void forgetOnDisconnect() {
		Platform.serverEvents().onPlayerDisconnect((player, server) -> server.execute(() -> forget(player.getUuid())));
	}
}
