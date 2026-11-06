package com.davisr.thedreamtable.fabric;

import com.davisr.thedreamtable.network.codec.CustomPayload;
import com.davisr.thedreamtable.network.codec.PacketCodec;
import com.davisr.thedreamtable.network.codec.PayloadTypes;
import com.davisr.thedreamtable.network.codec.RegistryByteBuf;
import com.davisr.thedreamtable.platform.ServerNetwork;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;

/** Fabric API networking for the server side: one channel per payload, named by its id. */
final class FabricServerNetwork implements ServerNetwork {
	@Override
	public <T extends CustomPayload> void registerServerToClient(CustomPayload.Id<T> id, PacketCodec<RegistryByteBuf, T> codec) {
		PayloadTypes.register(id, codec);
	}

	@Override
	public <T extends CustomPayload> void registerClientToServer(CustomPayload.Id<T> id, PacketCodec<RegistryByteBuf, T> codec, Handler<T> handler) {
		PayloadTypes.register(id, codec);
		// 1.20.1 calls this on the network thread with a buffer released afterwards, so it is decoded here and handled on the server thread.
		ServerPlayNetworking.registerGlobalReceiver(id.id(), (server, player, network, buf, sender) -> {
			T payload = PayloadTypes.decode(id, buf);
			server.execute(() -> handler.handle(payload, player));
		});
	}

	@Override
	public void send(ServerPlayerEntity player, CustomPayload payload) {
		if (player.networkHandler != null) {
			PacketByteBuf body = PayloadTypes.encodeOrWarn(payload);
			if (body != null) {
				ServerPlayNetworking.send(player, payload.getId().id(), body);
			}
		}
	}

	@Override
	public boolean canSend(ServerPlayerEntity player, CustomPayload.Id<?> id) {
		// A fake player has no connection, and Fabric's check reads it.
		return player.networkHandler != null && ServerPlayNetworking.canSend(player, id.id());
	}
}
