package com.davisr.thedreamtable.network;

import com.davisr.thedreamtable.farm.FarmPreferences;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** "Accept Farm Invites": the server refuses an invitation to a player who turned it off. */
class FarmInviteOptOutTest {
	private static final UUID OWNER = UUID.fromString("10000000-0000-0000-0000-000000000001");
	private static final UUID GUEST = UUID.fromString("20000000-0000-0000-0000-000000000002");

	@Test
	void invitesAreAcceptedByDefault() {
		assertTrue(FarmInvitePayload.acceptsInvites(new FarmPreferences(), OWNER, GUEST));
	}

	@Test
	void anOptedOutPlayerIsRefusedButOthersAreNot() {
		FarmPreferences preferences = new FarmPreferences();
		preferences.setAcceptsInvites(GUEST, false);
		assertFalse(FarmInvitePayload.acceptsInvites(preferences, OWNER, GUEST));
		assertTrue(FarmInvitePayload.acceptsInvites(preferences, GUEST, OWNER));
		preferences.setAcceptsInvites(GUEST, true);
		assertTrue(FarmInvitePayload.acceptsInvites(preferences, OWNER, GUEST));
	}

	@Test
	void invitingYourselfIsLeftToTheSelfCheck() {
		FarmPreferences preferences = new FarmPreferences();
		preferences.setAcceptsInvites(OWNER, false);
		assertTrue(FarmInvitePayload.acceptsInvites(preferences, OWNER, OWNER));
	}

	@Test
	void theNewResultIsAppendedSoOlderOrdinalsKeepTheirMeaning() {
		FarmActionResultPayload.Result[] results = FarmActionResultPayload.Result.values();
		assertEquals(FarmActionResultPayload.Result.INVITES_DISABLED, results[results.length - 1]);
		assertEquals(FarmActionResultPayload.Result.INVALID_INPUT, results[results.length - 2]);
	}

	@Test
	void everyResultHasAnEnglishNotice() throws Exception {
		try (Reader reader = new InputStreamReader(getClass().getResourceAsStream("/assets/the_dream_table/lang/en_us.json"), StandardCharsets.UTF_8)) {
			JsonObject lang = JsonParser.parseReader(reader).getAsJsonObject();
			for (FarmActionResultPayload.Result result : FarmActionResultPayload.Result.values()) {
				assertTrue(lang.has("gui.the_dream_table.action_result." + result.name().toLowerCase(Locale.ROOT)), result.name());
			}
		}
	}
}
