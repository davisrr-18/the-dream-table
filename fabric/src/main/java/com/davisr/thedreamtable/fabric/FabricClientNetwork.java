package com.davisr.thedreamtable.fabric;

import com.davisr.thedreamtable.network.codec.CustomPayload;
import com.davisr.thedreamtable.network.codec.PayloadTypes;
import com.davisr.thedreamtable.platform.ClientNetwork;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.PacketByteBuf;

import java.util.function.Consumer;

/** Fabric API networking for the client side. Only loaded by the client entrypoint. */
final class FabricClientNetwork implements ClientNetwork {
	@Override
	public <T extends CustomPayload> void registerReceiver(CustomPayload.Id<T> id, Consumer<T> receiver) {
		// Decoded on the network thread, where the buffer is still valid; received on the client thread.
		ClientPlayNetworking.registerGlobalReceiver(id.id(), (client, network, buf, sender) -> {
			T payload = PayloadTypes.decode(id, buf);
			client.execute(() -> receiver.accept(payload));
		});
	}

	@Override
	public boolean canSend(CustomPayload.Id<?> id) {
		return ClientPlayNetworking.canSend(id.id());
	}

	@Override
	public void send(CustomPayload payload) {
		PacketByteBuf body = PayloadTypes.encodeOrWarn(payload);
		if (body != null) {
			ClientPlayNetworking.send(payload.getId().id(), body);
		}
	}
}
