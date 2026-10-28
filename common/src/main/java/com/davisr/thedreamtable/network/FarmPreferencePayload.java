package com.davisr.thedreamtable.network;

import com.davisr.thedreamtable.TheDreamTableMod;
import com.davisr.thedreamtable.farm.FarmAction;
import com.davisr.thedreamtable.farm.FarmPreferences;
import com.davisr.thedreamtable.platform.Platform;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** Favorite and tracker changes for one currently accessible farm. */
public record FarmPreferencePayload(BlockPos table, String key, Operation operation) implements CustomPayload {
	public static final Id<FarmPreferencePayload> ID = new Id<>(Identifier.of(TheDreamTableMod.MOD_ID, "farm_preference"));
	public static final PacketCodec<RegistryByteBuf, FarmPreferencePayload> CODEC = PacketCodec.of(
			(payload, buf) -> {
				buf.writeBlockPos(payload.table());
				buf.writeString(payload.key(), 256);
				buf.writeEnumConstant(payload.operation());
			},
			buf -> new FarmPreferencePayload(buf.readBlockPos(), buf.readString(256), buf.readEnumConstant(Operation.class)));

	public enum Operation {
		FAVORITE, UNFAVORITE, TRACK, UNTRACK
	}

	public static void register() {
		Platform.network().registerClientToServer(ID, CODEC, (payload, player) -> handle(player, payload));
	}

	private static void handle(ServerPlayerEntity player, FarmPreferencePayload payload) {
		FarmAction action = payload.operation() == Operation.TRACK || payload.operation() == Operation.UNTRACK ? FarmAction.TRACK : FarmAction.FAVORITE;
		FarmActionResultPayload.Action resultAction = action == FarmAction.TRACK ? FarmActionResultPayload.Action.TRACK : FarmActionResultPayload.Action.FAVORITE;
		FarmNetwork.Checked checked = FarmNetwork.authorize(player, payload.table(), payload.key(), action, resultAction);
		if (checked == null) {
			return;
		}
		FarmPreferences preferences = FarmPreferences.of(player.getServer());
		boolean accepted = switch (payload.operation()) {
			case FAVORITE -> preferences.setFavorite(player.getUuid(), checked.farm().located().key(), true);
			case UNFAVORITE -> preferences.setFavorite(player.getUuid(), checked.farm().located().key(), false);
			case TRACK -> preferences.track(player.getUuid(), checked.farm().located().key());
			case UNTRACK -> {
				if (preferences.tracked(player.getUuid()).filter(checked.farm().located().key()::equals).isPresent()) {
					preferences.clearTracking(player.getUuid());
				}
				yield true;
			}
		};
		FarmNetwork.result(player, payload.key(), resultAction,
				accepted ? FarmActionResultPayload.Result.SUCCESS : FarmActionResultPayload.Result.LIMIT_REACHED);
		FarmNetwork.refresh(player);
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
