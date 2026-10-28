package com.davisr.thedreamtable.neoforge;

import com.davisr.thedreamtable.platform.ServerEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
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
	public void onServerStopped(Consumer<MinecraftServer> listener) {
		NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, event -> listener.accept(event.getServer()));
	}

	@Override
	public void onTickEnd(Consumer<MinecraftServer> listener) {
		NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, event -> listener.accept(event.getServer()));
	}

	@Override
	public void onPlayerJoin(BiConsumer<ServerPlayerEntity, MinecraftServer> listener) {
		NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, event -> {
			if (event.getEntity() instanceof ServerPlayerEntity player) {
				listener.accept(player, player.server);
			}
		});
	}

	@Override
	public void onBlockBreak(BlockBreakCheck check) {
		NeoForge.EVENT_BUS.addListener(BlockEvent.BreakEvent.class, event -> {
			if (event.getLevel() instanceof ServerWorld world && event.getPlayer() instanceof ServerPlayerEntity player
					&& !check.allows(world, player, event.getPos(), event.getState())) {
				event.setCanceled(true);
			}
		});
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
