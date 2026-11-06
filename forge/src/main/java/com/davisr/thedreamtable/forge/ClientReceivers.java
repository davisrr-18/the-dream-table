package com.davisr.thedreamtable.forge;

import com.davisr.thedreamtable.TheDreamTableMod;
import com.davisr.thedreamtable.network.codec.CustomPayload;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * The client's payload receivers. Forge registers each server-to-client payload together with its handler, so the
 * server side registers a handler that forwards here. Free of client classes so a dedicated server can load it.
 */
final class ClientReceivers {
	private static final Map<CustomPayload.Id<?>, Consumer<CustomPayload>> RECEIVERS = new ConcurrentHashMap<>();
	private static final Set<CustomPayload.Id<?>> WARNED = ConcurrentHashMap.newKeySet();

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
		} else if (WARNED.add(payload.getId())) {
			// A payload nobody listens for means a missing registration in the client setup; say so once instead of dropping it silently.
			TheDreamTableMod.LOGGER.warn("Dropping server payload {}: the client registered no receiver for it", payload.getId().id());
		}
	}
}
