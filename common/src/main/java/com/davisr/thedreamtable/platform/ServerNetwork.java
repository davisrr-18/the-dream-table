package com.davisr.thedreamtable.platform;

import com.davisr.thedreamtable.network.codec.RegistryByteBuf;
import com.davisr.thedreamtable.network.codec.PacketCodec;
import com.davisr.thedreamtable.network.codec.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;

/** Play-phase payload registration and sending on the logical server. */
public interface ServerNetwork {
	/** Registers a payload the server sends to clients. */
	<T extends CustomPayload> void registerServerToClient(CustomPayload.Id<T> id, PacketCodec<RegistryByteBuf, T> codec);

	/** Registers a payload clients send to the server; the handler always runs on the server thread. */
	<T extends CustomPayload> void registerClientToServer(CustomPayload.Id<T> id, PacketCodec<RegistryByteBuf, T> codec, Handler<T> handler);

	void send(ServerPlayerEntity player, CustomPayload payload);

	/** True when the player's client registered a receiver for the payload, so sending it will not be refused or ignored. */
	boolean canSend(ServerPlayerEntity player, CustomPayload.Id<?> id);

	@FunctionalInterface
	interface Handler<T extends CustomPayload> {
		void handle(T payload, ServerPlayerEntity player);
	}
}
