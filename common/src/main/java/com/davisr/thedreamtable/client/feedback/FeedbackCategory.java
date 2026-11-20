package com.davisr.thedreamtable.client.feedback;

/** The kinds of feedback the form sends. The wire name is the only value the feedback Worker accepts. */
public enum FeedbackCategory {
	BUG("bug"),
	IDEA("idea"),
	OTHER("other");

	private final String wire;

	FeedbackCategory(String wire) {
		this.wire = wire;
	}

	public String wire() {
		return wire;
	}
}
