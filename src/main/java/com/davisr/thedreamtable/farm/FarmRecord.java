package com.davisr.thedreamtable.farm;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Identity and access metadata needed to authorize one finished farm. */
public record FarmRecord(FarmKey key, UUID ownerUuid, Map<UUID, FarmGrant> grants) {
	public FarmRecord {
		Objects.requireNonNull(key, "key");
		Objects.requireNonNull(ownerUuid, "ownerUuid");
		grants = Map.copyOf(grants);
		if (grants.containsKey(ownerUuid)) {
			throw new IllegalArgumentException("The owner cannot also be a collaborator");
		}
		for (Map.Entry<UUID, FarmGrant> entry : grants.entrySet()) {
			if (!entry.getKey().equals(entry.getValue().playerUuid())) {
				throw new IllegalArgumentException("Grant key does not match its player");
			}
		}
	}
}
