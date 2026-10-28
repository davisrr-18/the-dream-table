package com.davisr.thedreamtable.platform;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Server lifecycle, tick and connection hooks, registered once at startup. Listeners run in registration order. */
public interface ServerEvents {
	/** Before the server's worlds load. */
	void onServerStarting(Consumer<MinecraftServer> listener);

	/** After the server finished starting and its worlds are loaded. */
	void onServerStarted(Consumer<MinecraftServer> listener);

	/** After the server shut down and saved its worlds; per-server static state is cleared here. */
	void onServerStopped(Consumer<MinecraftServer> listener);

	/** At the end of every server tick. */
	void onTickEnd(Consumer<MinecraftServer> listener);

	/** When a player leaves the server; the player is still readable. */
	void onPlayerDisconnect(BiConsumer<ServerPlayerEntity, MinecraftServer> listener);
}
