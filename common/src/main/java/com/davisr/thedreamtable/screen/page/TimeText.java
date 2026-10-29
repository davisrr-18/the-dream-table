package com.davisr.thedreamtable.screen.page;

import net.minecraft.text.Text;

/** A short time estimate: "~45 s" under a minute, "~4 min" from there on, rounded up. */
public final class TimeText {
	private TimeText() {
	}

	public static Text of(int seconds) {
		return seconds >= 60
				? Text.translatable("gui.the_dream_table.eta_minutes", (seconds + 59) / 60)
				: Text.translatable("gui.the_dream_table.eta_seconds", seconds);
	}
}
