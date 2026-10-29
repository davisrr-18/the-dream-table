package com.davisr.thedreamtable.client;

import com.davisr.thedreamtable.network.TableNoticePayload.Severity;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

/**
 * The one table notice on show: the newest replaces the older, it stays for {@link #LIFETIME_MS} and fades during its
 * last {@link #FADE_MS}. Under it sits an optional persistent line, such as a running build's live progress, which has
 * no timer: it shows while no ordinary notice does and is replaced in place by its owner. Time is passed in so the
 * timeline can be tested without a client.
 */
public final class NoticeBoard {
	public static final long LIFETIME_MS = 5000;
	public static final long FADE_MS = 1000;

	public record Notice(Text text, Severity severity, long shownAt, boolean persistent) {
		/** Opacity at {@code now}: a persistent line never fades. */
		public float alpha(long now) {
			return persistent ? 1.0F : NoticeBoard.alpha(now - shownAt);
		}
	}

	private Notice current;
	private Notice persistent;

	public void show(Text text, Severity severity, long now) {
		current = new Notice(text, severity, now, false);
	}

	/** Sets the persistent line, or removes it when {@code text} is null. It never expires by itself. */
	public void setPersistent(@Nullable Text text, Severity severity) {
		persistent = text == null ? null : new Notice(text, severity, 0, true);
	}

	/** The notice to draw at {@code now}: the ordinary one while it lasts, otherwise the persistent line, otherwise null. */
	public @Nullable Notice current(long now) {
		return current(now, true);
	}

	/** As {@link #current(long)}, but {@code withPersistent} false leaves the persistent line out (while it is shown elsewhere). */
	public @Nullable Notice current(long now, boolean withPersistent) {
		if (current != null && now - current.shownAt() >= LIFETIME_MS) {
			current = null;
		}
		return current != null ? current : withPersistent ? persistent : null;
	}

	public void clear() {
		current = null;
		persistent = null;
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
