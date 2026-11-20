package com.davisr.thedreamtable.client.feedback;

import java.util.Optional;

/** Whether Send may run now and, when it may not, the reason the button names on its second line. */
public final class FeedbackRules {
	/** The pause between two sends from one client, so a player cannot flood the Worker. */
	public static final long COOLDOWN_MILLIS = 30_000L;

	public enum Block {
		NOT_AVAILABLE, BUSY, COOLDOWN, TOO_SHORT, TOO_LONG
	}

	private FeedbackRules() {
	}

	public static Optional<Block> blocked(String rawText, boolean available, boolean busy, long now, long nextAllowed) {
		int length = FeedbackJson.cleanText(rawText).length();
		if (!available) {
			return Optional.of(Block.NOT_AVAILABLE);
		}
		if (busy) {
			return Optional.of(Block.BUSY);
		}
		if (now < nextAllowed) {
			return Optional.of(Block.COOLDOWN);
		}
		if (length < FeedbackJson.MIN_LENGTH) {
			return Optional.of(Block.TOO_SHORT);
		}
		if (length > FeedbackJson.MAX_LENGTH) {
			return Optional.of(Block.TOO_LONG);
		}
		return Optional.empty();
	}

	public static long nextAllowedAfter(long finishedAt) {
		return finishedAt + COOLDOWN_MILLIS;
	}
}
