package com.davisr.thedreamtable.fabric;

import com.davisr.thedreamtable.platform.ClientNetwork;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.packet.CustomPayload;

import java.util.function.Consumer;

/** Fabric API networking for the client side. Only loaded by the client entrypoint. */
final class FabricClientNetwork implements ClientNetwork {
	@Override
	public <T extends CustomPayload> void registerReceiver(CustomPayload.Id<T> id, Consumer<T> receiver) {
		ClientPlayNetworking.registerGlobalReceiver(id, (payload, context) -> receiver.accept(payload));
	}

	@Override
	public boolean canSend(CustomPayload.Id<?> id) {
		return ClientPlayNetworking.canSend(id);
	}

	@Override
	public void send(CustomPayload payload) {
		ClientPlayNetworking.send(payload);
	}
}
