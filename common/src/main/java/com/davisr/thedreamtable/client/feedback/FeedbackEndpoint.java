package com.davisr.thedreamtable.client.feedback;

/** Where feedback is posted. Empty means the form says it is not available yet and nothing is sent. */
public final class FeedbackEndpoint {
	public static final String URL = "https://the-dream-table-feedback.rede-neural-mcp.workers.dev/";

	private FeedbackEndpoint() {
	}

	public static boolean available() {
		return !URL.isBlank();
	}
}
