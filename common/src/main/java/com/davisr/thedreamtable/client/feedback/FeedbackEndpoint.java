package com.davisr.thedreamtable.client.feedback;

/** Where feedback is posted. Empty means the form says it is not available yet and nothing is sent. */
public final class FeedbackEndpoint {
	// shortcut: URL stays empty, so Send is off, until the feedback Worker is deployed; set it then.
	public static final String URL = "";

	private FeedbackEndpoint() {
	}

	public static boolean available() {
		return !URL.isBlank();
	}
}
