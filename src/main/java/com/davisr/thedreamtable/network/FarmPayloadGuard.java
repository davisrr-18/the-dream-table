package com.davisr.thedreamtable.network;

import com.davisr.thedreamtable.farm.FarmAccess;
import com.davisr.thedreamtable.farm.FarmAction;
import com.davisr.thedreamtable.farm.FarmKey;
import com.davisr.thedreamtable.farm.FarmRecord;
import com.davisr.thedreamtable.farm.FarmRole;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Pure authorization and public-key validation shared by every cooperative network handler. */
public final class FarmPayloadGuard {
	private FarmPayloadGuard() {
	}

	public record Authorization(FarmActionResultPayload.Result result, @Nullable FarmRole role) {
	}

	public record Parsed(FarmActionResultPayload.Result result, @Nullable FarmKey key) {
	}

	public static Authorization authorize(@Nullable FarmRecord farm, UUID actorUuid, FarmAction action, boolean sharingEnabled) {
		if (farm == null) {
			return new Authorization(FarmActionResultPayload.Result.NOT_FOUND, null);
		}
		return FarmAccess.role(farm, actorUuid, sharingEnabled)
				.filter(role -> FarmAccess.allows(role, action))
				.map(role -> new Authorization(FarmActionResultPayload.Result.SUCCESS, role))
				.orElseGet(() -> new Authorization(FarmActionResultPayload.Result.FORBIDDEN, null));
	}

	public static Parsed parse(String externalKey) {
		try {
			return new Parsed(FarmActionResultPayload.Result.SUCCESS, FarmKey.parse(externalKey));
		} catch (IllegalArgumentException malformed) {
			return new Parsed(FarmActionResultPayload.Result.INVALID_INPUT, null);
		}
	}
}
