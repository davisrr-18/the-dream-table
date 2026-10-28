package com.davisr.thedreamtable.neoforge;

import com.davisr.thedreamtable.platform.ServerNetwork;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * NeoForge networking for the server side. NeoForge accepts payloads only during its registration event, so the
 * registrations made at startup are kept and applied, in the same order, when that event fires.
 */
final class NeoForgeServerNetwork implements ServerNetwork {
	/** Bumped when a payload's id or codec changes, so mismatched installs refuse to connect. */
	private static final String PROTOCOL = "1";

	private final List<Consumer<PayloadRegistrar>> registrations = new ArrayList<>();

	NeoForgeServerNetwork(IEventBus modBus) {
		modBus.addListener(RegisterPayloadHandlersEvent.class, event -> {
			PayloadRegistrar registrar = event.registrar(PROTOCOL);
			registrations.forEach(registration -> registration.accept(registrar));
		});
	}

	@Override
	public <T extends CustomPayload> void registerServerToClient(CustomPayload.Id<T> id, PacketCodec<RegistryByteBuf, T> codec) {
		// Handlers run on the receiving side's main thread by default.
		registrations.add(registrar -> registrar.playToClient(id, codec, (payload, context) -> ClientReceivers.receive(payload)));
	}

	@Override
	public <T extends CustomPayload> void registerClientToServer(CustomPayload.Id<T> id, PacketCodec<RegistryByteBuf, T> codec, Handler<T> handler) {
		registrations.add(registrar -> registrar.playToServer(id, codec, (payload, context) -> {
			if (context.player() instanceof ServerPlayerEntity player) {
				handler.handle(payload, player);
			}
		}));
	}

	@Override
	public void send(ServerPlayerEntity player, CustomPayload payload) {
		PacketDistributor.sendToPlayer(player, payload);
	}
}
