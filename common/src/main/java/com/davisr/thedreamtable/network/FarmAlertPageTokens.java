package com.davisr.thedreamtable.network;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Server-only current-page capabilities for alert acknowledgements. */
final class FarmAlertPageTokens {
	private static final int MAX_REFERENCES = 50;

	private record Page(String token, String key, Set<Long> references) {
	}

	private final Map<UUID, Page> current = new HashMap<>();

	String issue(UUID playerUuid, String key, List<Long> references) {
		Set<Long> bounded = new LinkedHashSet<>();
		for (Long reference : references) {
			if (reference != null && reference > 0 && bounded.size() < MAX_REFERENCES) {
				bounded.add(reference);
			}
		}
		String token = UUID.randomUUID().toString();
		current.put(playerUuid, new Page(token, key, Set.copyOf(bounded)));
		return token;
	}

	/** Drops a leaving player's page, so the map holds only the players who are online. */
	void forget(UUID playerUuid) {
		current.remove(playerUuid);
	}

	boolean accept(UUID playerUuid, String token, String key, List<Long> references) {
		Page page = current.get(playerUuid);
		if (page == null || !page.token().equals(token) || !page.key().equals(key) || references.size() > MAX_REFERENCES) {
			return false;
		}
		for (Long reference : references) {
			if (reference == null || !page.references().contains(reference)) {
				return false;
			}
		}
		return true;
	}
}
