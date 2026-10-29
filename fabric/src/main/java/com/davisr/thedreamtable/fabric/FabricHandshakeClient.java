package com.davisr.thedreamtable.fabric;

import com.davisr.thedreamtable.network.NetworkProtocol;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;

/**
 * The client half of {@link FabricHandshake}: answers the server's request with this install's protocol. Registering the
 * receiver is also what tells the server this client has the mod. Only loaded by the client entrypoint.
 */
final class FabricHandshakeClient {
	private FabricHandshakeClient() {
	}

	static void register() {
		ClientConfigurationNetworking.registerGlobalReceiver(FabricHandshake.REQUEST_ID, (request, context) ->
				context.responseSender().sendPacket(new FabricHandshake.Reply(NetworkProtocol.VERSION, FabricHandshake.modVersion())));
	}
}
