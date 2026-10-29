package com.davisr.thedreamtable.screen.ui;

import com.davisr.thedreamtable.screen.ui.layout.UiRect;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.util.Objects;

/** Everything needed to draw, focus, narrate, and activate one control. */
public record ControlSpec(
		ControlId id,
		ControlGroup group,
		UiRect bounds,
		Text label,
		Text state,
		boolean enabled,
		boolean selected,
		Text disabledReason,
		int row,
		int column
) {
	public ControlSpec {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(group, "group");
		Objects.requireNonNull(bounds, "bounds");
		Objects.requireNonNull(label, "label");
		state = state == null ? Text.empty() : state;
		disabledReason = disabledReason == null ? Text.empty() : disabledReason;
	}

	public Text narration() {
		MutableText narration = Text.empty().append(label);
		append(narration, state);
		if (selected) {
			append(narration, Text.translatable("gui.the_dream_table.narration.selected"));
		}
		if (!enabled) {
			Text unavailable = Text.translatable("gui.the_dream_table.narration.disabled");
			append(narration, unavailable);
			// A reason that only repeats the generic word is not read twice.
			if (!disabledReason.getString().equals(unavailable.getString())) {
				append(narration, disabledReason);
			}
		}
		return narration;
	}

	private static void append(MutableText narration, Text part) {
		if (!part.getString().isBlank()) {
			narration.append(Text.literal(", ")).append(part);
		}
	}
}
