package com.davisr.thedreamtable.screen.ui;

import java.util.Objects;

/** Stable identity for a control across refreshes, scrolling, and layout changes. */
public record ControlId(String value) {
	public ControlId {
		Objects.requireNonNull(value, "value");
		if (value.isBlank()) {
			throw new IllegalArgumentException("Control id cannot be blank");
		}
	}

	public static ControlId of(String scope, String value) {
		return new ControlId(scope + "." + value);
	}
}
