package com.davisr.thedreamtable.forge;

import com.davisr.thedreamtable.platform.ClientNetwork;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.CustomPayload;

import java.util.function.Consumer;

/** Forge networking for the client side, on the server side's channel. Only loaded by the client setup. */
final class ForgeClientNetwork implements ClientNetwork {
	private final ForgeServerNetwork network;

	ForgeClientNetwork(ForgeServerNetwork network) {
		this.network = network;
	}

	@Override
	public <T extends CustomPayload> void registerReceiver(CustomPayload.Id<T> id, Consumer<T> receiver) {
		ClientReceivers.register(id, receiver);
	}

	@Override
	public boolean canSend(CustomPayload.Id<?> id) {
		ClientPlayNetworkHandler handler = MinecraftClient.getInstance().getNetworkHandler();
		return handler != null && network.channel().isRemotePresent(handler.getConnection());
	}

	@Override
	public void send(CustomPayload payload) {
		ClientPlayNetworkHandler handler = MinecraftClient.getInstance().getNetworkHandler();
		if (handler != null) {
			network.channel().send(payload, handler.getConnection());
		}
	}
}
