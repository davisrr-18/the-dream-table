package com.davisr.thedreamtable.platform;

import com.davisr.thedreamtable.network.codec.CustomPayload;

import java.util.function.Consumer;

/** Play-phase payload receiving and sending on the client. Free of client classes so a dedicated server can load it. */
public interface ClientNetwork {
	/** The receiver always runs on the client thread. */
	<T extends CustomPayload> void registerReceiver(CustomPayload.Id<T> id, Consumer<T> receiver);

	/** True when the connected server registered a receiver for the payload (false while not in a world). */
	boolean canSend(CustomPayload.Id<?> id);

	void send(CustomPayload payload);
}
