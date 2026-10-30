package com.davisr.thedreamtable.farm;

import java.util.UUID;

/**
 * The one access rule for finished farms, evaluated from server-owned records: only the farm's owner may act on it.
 * Server operators keep their own power over protected farms, in {@link FarmProtection}, not here.
 */
public final class FarmAccess {
	private FarmAccess() {
	}

	public static boolean isOwner(FarmRecord farm, UUID actorUuid) {
		return farm.ownerUuid().equals(actorUuid);
	}

	/** Every server-authorized action is owner-only; the action names what the request does, for the caller's reading. */
	public static boolean allows(FarmRecord farm, UUID actorUuid, FarmAction action) {
		return isOwner(farm, actorUuid);
	}
}
