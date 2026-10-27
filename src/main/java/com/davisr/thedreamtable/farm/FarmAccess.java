package com.davisr.thedreamtable.farm;

import java.util.Optional;
import java.util.UUID;

/** Central role and capability matrix, always evaluated from server-owned records. */
public final class FarmAccess {
	private FarmAccess() {
	}

	public static Optional<FarmRole> role(FarmRecord farm, UUID actorUuid, boolean sharingEnabled) {
		if (farm.ownerUuid().equals(actorUuid)) {
			return Optional.of(FarmRole.OWNER);
		}
		if (!sharingEnabled) {
			return Optional.empty();
		}
		FarmGrant grant = farm.grants().get(actorUuid);
		return grant == null ? Optional.empty() : Optional.of(grant.role());
	}

	public static boolean allows(FarmRecord farm, UUID actorUuid, FarmAction action, boolean sharingEnabled) {
		return role(farm, actorUuid, sharingEnabled).map(role -> allows(role, action)).orElse(false);
	}

	public static boolean allows(FarmRole role, FarmAction action) {
		return switch (role) {
			case OWNER -> true;
			case OPERATOR -> switch (action) {
				case VIEW, FAVORITE, TRACK, ALERTS, REPAIR, KEEP_LOADED -> true;
				default -> false;
			};
			case VIEWER -> switch (action) {
				case VIEW, FAVORITE, TRACK, ALERTS -> true;
				default -> false;
			};
		};
	}
}
