package com.davisr.thedreamtable.client;

import com.davisr.thedreamtable.network.TableNoticePayload.Severity;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/**
 * The one table notice on show: the newest replaces the older, it stays for {@link #LIFETIME_MS} and fades during its
 * last {@link #FADE_MS}. Time is passed in so the timeline can be tested without a client.
 */
public final class NoticeBoard {
	public static final long LIFETIME_MS = 5000;
	public static final long FADE_MS = 1000;

	public record Notice(Text text, Severity severity, long shownAt) {
	}

	private Notice current;

	public void show(Text text, Severity severity, long now) {
		current = new Notice(text, severity, now);
	}

	/** The notice to draw at {@code now}, or null once it has expired. */
	public @Nullable Notice current(long now) {
		if (current != null && now - current.shownAt() >= LIFETIME_MS) {
			current = null;
		}
		return current;
	}

	public void clear() {
		current = null;
	}

	/** Opacity of a notice of the given age: fully opaque until the fade starts, then falling to zero at its end. */
	public static float alpha(long ageMs) {
		if (ageMs < 0 || ageMs >= LIFETIME_MS) {
			return ageMs < 0 ? 1.0F : 0.0F;
		}
		long fadeStart = LIFETIME_MS - FADE_MS;
		return ageMs <= fadeStart ? 1.0F : (float) (LIFETIME_MS - ageMs) / FADE_MS;
	}
}
