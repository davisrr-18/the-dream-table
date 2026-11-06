package com.davisr.thedreamtable.fabric;

import com.davisr.thedreamtable.network.NetworkProtocol;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginNetworking;

import java.util.concurrent.CompletableFuture;

/**
 * The client half of {@link FabricHandshake}: answers the server's login query with this install's protocol. Answering
 * at all is what tells the server this client has the mod. Only loaded by the client entrypoint.
 */
final class FabricHandshakeClient {
	private FabricHandshakeClient() {
	}

	static void register() {
		ClientLoginNetworking.registerGlobalReceiver(FabricHandshake.REQUEST_ID.id(), (client, handler, buf, listeners) ->
				CompletableFuture.completedFuture(FabricHandshake.encode(FabricHandshake.Reply.CODEC,
						new FabricHandshake.Reply(NetworkProtocol.VERSION, FabricHandshake.modVersion()))));
	}
}
