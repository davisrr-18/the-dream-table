package com.davisr.thedreamtable.neoforge;

import net.minecraft.network.packet.CustomPayload;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * The client's payload receivers. NeoForge registers each server-to-client payload together with its handler, so the
 * server side registers a handler that forwards here. Free of client classes so a dedicated server can load it.
 */
final class ClientReceivers {
	private static final Map<CustomPayload.Id<?>, Consumer<CustomPayload>> RECEIVERS = new ConcurrentHashMap<>();

	private ClientReceivers() {
	}

	@SuppressWarnings("unchecked")
	static <T extends CustomPayload> void register(CustomPayload.Id<T> id, Consumer<T> receiver) {
		RECEIVERS.put(id, payload -> receiver.accept((T) payload));
	}

	static void receive(CustomPayload payload) {
		Consumer<CustomPayload> receiver = RECEIVERS.get(payload.getId());
		if (receiver != null) {
			receiver.accept(payload);
		}
	}
}
