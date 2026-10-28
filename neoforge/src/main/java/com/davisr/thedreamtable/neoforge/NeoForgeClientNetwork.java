package com.davisr.thedreamtable.neoforge;

import com.davisr.thedreamtable.platform.ClientNetwork;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.CustomPayload;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.function.Consumer;

/** NeoForge networking for the client side. Only loaded by the client setup. */
final class NeoForgeClientNetwork implements ClientNetwork {
	@Override
	public <T extends CustomPayload> void registerReceiver(CustomPayload.Id<T> id, Consumer<T> receiver) {
		ClientReceivers.register(id, receiver);
	}

	@Override
	public boolean canSend(CustomPayload.Id<?> id) {
		ClientPlayNetworkHandler handler = MinecraftClient.getInstance().getNetworkHandler();
		return handler != null && handler.hasChannel(id);
	}

	@Override
	public void send(CustomPayload payload) {
		PacketDistributor.sendToServer(payload);
	}
}
