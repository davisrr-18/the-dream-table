package com.davisr.thedreamtable.platform;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;

/** Play-phase payload registration and sending on the logical server. */
public interface ServerNetwork {
	/** Registers a payload the server sends to clients. */
	<T extends CustomPayload> void registerServerToClient(CustomPayload.Id<T> id, PacketCodec<RegistryByteBuf, T> codec);

	/** Registers a payload clients send to the server; the handler always runs on the server thread. */
	<T extends CustomPayload> void registerClientToServer(CustomPayload.Id<T> id, PacketCodec<RegistryByteBuf, T> codec, Handler<T> handler);

	void send(ServerPlayerEntity player, CustomPayload payload);

	@FunctionalInterface
	interface Handler<T extends CustomPayload> {
		void handle(T payload, ServerPlayerEntity player);
	}
}
