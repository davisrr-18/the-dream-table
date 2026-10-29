package com.davisr.thedreamtable.fabric;

import com.davisr.thedreamtable.platform.ServerNetwork;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;

/** Fabric API networking for the server side. */
final class FabricServerNetwork implements ServerNetwork {
	@Override
	public <T extends CustomPayload> void registerServerToClient(CustomPayload.Id<T> id, PacketCodec<RegistryByteBuf, T> codec) {
		PayloadTypeRegistry.playS2C().register(id, codec);
	}

	@Override
	public <T extends CustomPayload> void registerClientToServer(CustomPayload.Id<T> id, PacketCodec<RegistryByteBuf, T> codec, Handler<T> handler) {
		PayloadTypeRegistry.playC2S().register(id, codec);
		ServerPlayNetworking.registerGlobalReceiver(id, (payload, context) -> context.server().execute(() -> handler.handle(payload, context.player())));
	}

	@Override
	public void send(ServerPlayerEntity player, CustomPayload payload) {
		if (player.networkHandler != null) {
			ServerPlayNetworking.send(player, payload);
		}
	}

	@Override
	public boolean canSend(ServerPlayerEntity player, CustomPayload.Id<?> id) {
		// A fake player has no connection, and Fabric's check reads it.
		return player.networkHandler != null && ServerPlayNetworking.canSend(player, id);
	}
}
