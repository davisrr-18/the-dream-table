package com.davisr.thedreamtable.network;

import com.davisr.thedreamtable.TheDreamTableMod;
import com.davisr.thedreamtable.platform.Platform;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Explicit result for a cooperative farm request; clients never infer success from optimistic UI state. */
public record FarmActionResultPayload(String key, Action action, Result result) implements CustomPayload {
	private static final int MAX_KEY_LENGTH = 256;
	public static final Id<FarmActionResultPayload> ID = new Id<>(Identifier.of(TheDreamTableMod.MOD_ID, "farm_action_result"));
	public static final PacketCodec<RegistryByteBuf, FarmActionResultPayload> CODEC = PacketCodec.of(
			(payload, buf) -> {
				buf.writeString(payload.key(), MAX_KEY_LENGTH);
				buf.writeEnumConstant(payload.action());
				buf.writeEnumConstant(payload.result());
			},
			buf -> new FarmActionResultPayload(buf.readString(MAX_KEY_LENGTH), buf.readEnumConstant(Action.class), buf.readEnumConstant(Result.class)));

	public enum Action {
		DETAILS, RENAME, INVITE, ACCEPT_INVITE, DECLINE_INVITE, REMOVE_COLLABORATOR, FAVORITE, TRACK,
		REPAIR, TAKE_DOWN, FORGET, KEEP_LOADED
	}

	public enum Result {
		SUCCESS, NOT_FOUND, FORBIDDEN, LIMIT_REACHED, EXPIRED, BUSY, INVALID_INPUT
	}

	public static void register() {
		Platform.network().registerServerToClient(ID, CODEC);
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
