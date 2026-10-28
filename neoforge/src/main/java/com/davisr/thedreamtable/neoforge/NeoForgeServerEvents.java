package com.davisr.thedreamtable.neoforge;

import com.davisr.thedreamtable.platform.ServerEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** NeoForge's server lifecycle, tick and connection events. */
final class NeoForgeServerEvents implements ServerEvents {
	@Override
	public void onServerStarting(Consumer<MinecraftServer> listener) {
		// NeoForge's ServerStartingEvent fires after the worlds load; this one fires before, like Fabric's SERVER_STARTING.
		NeoForge.EVENT_BUS.addListener(ServerAboutToStartEvent.class, event -> listener.accept(event.getServer()));
	}

	@Override
	public void onServerStarted(Consumer<MinecraftServer> listener) {
		NeoForge.EVENT_BUS.addListener(ServerStartedEvent.class, event -> listener.accept(event.getServer()));
	}

	@Override
	public void onTickEnd(Consumer<MinecraftServer> listener) {
		NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, event -> listener.accept(event.getServer()));
	}

	@Override
	public void onPlayerDisconnect(BiConsumer<ServerPlayerEntity, MinecraftServer> listener) {
		NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, event -> {
			if (event.getEntity() instanceof ServerPlayerEntity player) {
				listener.accept(player, player.server);
			}
		});
	}
}
