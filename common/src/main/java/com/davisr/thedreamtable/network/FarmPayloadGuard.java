package com.davisr.thedreamtable.network;

import com.davisr.thedreamtable.farm.FarmAccess;
import com.davisr.thedreamtable.farm.FarmAction;
import com.davisr.thedreamtable.farm.FarmKey;
import com.davisr.thedreamtable.farm.FarmRecord;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Pure authorization and public-key validation shared by every cooperative network handler. */
public final class FarmPayloadGuard {
	private FarmPayloadGuard() {
	}

	public record Parsed(FarmActionResultPayload.Result result, @Nullable FarmKey key) {
	}

	public static FarmActionResultPayload.Result authorize(@Nullable FarmRecord farm, UUID actorUuid, FarmAction action) {
		if (farm == null) {
			return FarmActionResultPayload.Result.NOT_FOUND;
		}
		return FarmAccess.allows(farm, actorUuid, action) ? FarmActionResultPayload.Result.SUCCESS : FarmActionResultPayload.Result.FORBIDDEN;
	}

	public static Parsed parse(String externalKey) {
		try {
			return new Parsed(FarmActionResultPayload.Result.SUCCESS, FarmKey.parse(externalKey));
		} catch (IllegalArgumentException malformed) {
			return new Parsed(FarmActionResultPayload.Result.INVALID_INPUT, null);
		}
	}
}
