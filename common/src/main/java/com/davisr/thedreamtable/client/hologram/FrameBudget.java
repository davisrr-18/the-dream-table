package com.davisr.thedreamtable.client.hologram;

import java.util.function.LongSupplier;

/**
 * How much of a frame the hologram may spend building geometry. Builders do a slice of work, then ask
 * {@link #spent()}; because they always finish a slice first, every frame makes progress even on a slow machine.
 * Also counts frames, so caches can tell what was used this frame.
 */
public final class FrameBudget {
	private final long nanosPerFrame;
	private final LongSupplier clock;
	private long frame;
	private long started;

	public FrameBudget(long nanosPerFrame, LongSupplier clock) {
		this.nanosPerFrame = nanosPerFrame;
		this.clock = clock;
		this.started = clock.getAsLong();
	}

	/** Starts the next frame's allowance and returns its number. */
	public long beginFrame() {
		frame++;
		started = clock.getAsLong();
		return frame;
	}

	public long frame() {
		return frame;
	}

	/** True once this frame has used up its allowance. */
	public boolean spent() {
		return clock.getAsLong() - started >= nanosPerFrame;
	}
}
