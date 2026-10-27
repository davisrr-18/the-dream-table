package com.davisr.thedreamtable.network;

import com.davisr.thedreamtable.TheDreamTableMod;
import com.davisr.thedreamtable.block.entity.DreamTableBlockEntity;
import com.davisr.thedreamtable.block.entity.FarmCare;
import com.davisr.thedreamtable.block.entity.TableSession;
import com.davisr.thedreamtable.farm.FarmAreas;
import com.davisr.thedreamtable.farm.FarmAction;
import com.davisr.thedreamtable.farm.FarmLoader;
import com.davisr.thedreamtable.farm.FarmRole;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** A My Farms action addressed by the farm's stable key, never by its current place in a changing list. */
public record FarmCarePayload(BlockPos table, String key, Action action) implements CustomPayload {
	private static final int MAX_KEY_LENGTH = 256;
	public static final Id<FarmCarePayload> ID = new Id<>(Identifier.of(TheDreamTableMod.MOD_ID, "farm_care"));
	public static final PacketCodec<RegistryByteBuf, FarmCarePayload> CODEC = PacketCodec.of(
			(payload, buf) -> {
				buf.writeBlockPos(payload.table());
				buf.writeString(payload.key(), MAX_KEY_LENGTH);
				buf.writeEnumConstant(payload.action());
			},
			buf -> new FarmCarePayload(buf.readBlockPos(), buf.readString(MAX_KEY_LENGTH), buf.readEnumConstant(Action.class)));

	public enum Action {
		REPAIR, TAKE_DOWN, FORGET
	}

	public static void register() {
		PayloadTypeRegistry.playC2S().register(ID, CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ID, (payload, context) -> context.server().execute(() -> handle(context.player(), payload)));
	}

	private static void handle(ServerPlayerEntity player, FarmCarePayload payload) {
		FarmAction action = switch (payload.action()) {
			case REPAIR -> FarmAction.REPAIR;
			case TAKE_DOWN -> FarmAction.TAKE_DOWN;
			case FORGET -> FarmAction.FORGET;
		};
		FarmActionResultPayload.Action resultAction = switch (payload.action()) {
			case REPAIR -> FarmActionResultPayload.Action.REPAIR;
			case TAKE_DOWN -> FarmActionResultPayload.Action.TAKE_DOWN;
			case FORGET -> FarmActionResultPayload.Action.FORGET;
		};
		FarmNetwork.Checked checked = FarmNetwork.authorize(player, payload.table(), payload.key(), action, resultAction);
		if (checked == null) {
			return;
		}
		DreamTableBlockEntity table = checked.table();
		TableSession session = table.session(player);
		FarmLoader.Located farm = new FarmLoader.Located(payload.key(), checked.farm().located().world(), checked.farm().located().farm());
		FarmActionResultPayload.Result result = switch (payload.action()) {
			case REPAIR -> checked.farm().role() != FarmRole.OWNER ? FarmActionResultPayload.Result.BUSY
					: FarmCare.repair(session, player, farm) ? FarmActionResultPayload.Result.SUCCESS : FarmActionResultPayload.Result.BUSY;
			case TAKE_DOWN -> FarmCare.takeDown(session, player, farm)
					? FarmActionResultPayload.Result.SUCCESS : FarmActionResultPayload.Result.BUSY;
			case FORGET -> {
				if (farm.farm().layout() != null) {
					yield FarmActionResultPayload.Result.INVALID_INPUT;
				}
				FarmAreas.of(farm.world()).release(farm.farm().key());
				yield FarmActionResultPayload.Result.SUCCESS;
			}
		};
		FarmNetwork.result(player, payload.key(), resultAction, result);
		FarmNetwork.refresh(player);
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
