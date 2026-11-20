package com.davisr.thedreamtable.client.feedback;

import com.davisr.thedreamtable.TheDreamTableMod;
import com.davisr.thedreamtable.client.TableNotices;
import com.davisr.thedreamtable.platform.ModInfo;
import com.davisr.thedreamtable.platform.Platform;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Posts one feedback text at a time and reports the outcome on the client thread. The text is never logged; a failure
 * logs only its exception type or status code.
 */
public final class FeedbackSender {
	private static final Duration TIMEOUT = Duration.ofSeconds(10);
	private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
	private static boolean sending;
	private static long nextAllowed;

	private FeedbackSender() {
	}

	public static boolean sending() {
		return sending;
	}

	/** The time, in {@link Util#getMeasuringTimeMs()} milliseconds, before which the next send waits. */
	public static long nextAllowed() {
		return nextAllowed;
	}

	/** Sends the text unless one send is already running; {@code sent} runs on the client thread once the Worker accepts it. */
	public static void send(String rawText, FeedbackCategory category, Runnable sent) {
		if (sending || !FeedbackEndpoint.available()) {
			return;
		}
		ModInfo info = Platform.modInfo();
		String json = FeedbackJson.body(rawText, category, info.version(), info.loader(), SharedConstants.getGameVersion().getName());
		HttpRequest request = HttpRequest.newBuilder(URI.create(FeedbackEndpoint.URL))
				.timeout(TIMEOUT)
				.header("Content-Type", "application/json; charset=utf-8")
				.header("User-Agent", "TheDreamTable/" + info.version())
				.POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
				.build();
		sending = true;
		CLIENT.sendAsync(request, HttpResponse.BodyHandlers.discarding())
				.whenComplete((response, error) -> MinecraftClient.getInstance().execute(() -> finish(response, error, sent)));
	}

	private static void finish(HttpResponse<Void> response, Throwable error, Runnable sent) {
		sending = false;
		nextAllowed = FeedbackRules.nextAllowedAfter(Util.getMeasuringTimeMs());
		if (error != null) {
			TheDreamTableMod.LOGGER.warn("Feedback was not sent: {}", error.getClass().getSimpleName());
			TableNotices.problem(Text.translatable("gui.the_dream_table.feedback.offline"));
		} else if (response.statusCode() == 429) {
			TableNotices.problem(Text.translatable("gui.the_dream_table.feedback.rate_limited"));
		} else if (response.statusCode() / 100 == 2) {
			TableNotices.info(Text.translatable("gui.the_dream_table.feedback.sent"));
			sent.run();
		} else {
			TheDreamTableMod.LOGGER.warn("Feedback was rejected with status {}", response.statusCode());
			TableNotices.problem(Text.translatable("gui.the_dream_table.feedback.failed", response.statusCode()));
		}
	}
}
