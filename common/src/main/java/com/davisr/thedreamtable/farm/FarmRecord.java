package com.davisr.thedreamtable.farm;

import java.util.Objects;
import java.util.UUID;

/** Identity and owner needed to authorize one finished farm. Only the owner may act on a farm. */
public record FarmRecord(FarmKey key, UUID ownerUuid) {
	public FarmRecord {
		Objects.requireNonNull(key, "key");
		Objects.requireNonNull(ownerUuid, "ownerUuid");
	}
}
