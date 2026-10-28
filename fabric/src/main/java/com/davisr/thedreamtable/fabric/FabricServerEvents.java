package com.davisr.thedreamtable.fabric;

import com.davisr.thedreamtable.platform.ServerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Fabric API's server lifecycle, tick and connection events. */
final class FabricServerEvents implements ServerEvents {
	@Override
	public void onServerStarting(Consumer<MinecraftServer> listener) {
		ServerLifecycleEvents.SERVER_STARTING.register(listener::accept);
	}

	@Override
	public void onServerStarted(Consumer<MinecraftServer> listener) {
		ServerLifecycleEvents.SERVER_STARTED.register(listener::accept);
	}

	@Override
	public void onServerStopped(Consumer<MinecraftServer> listener) {
		ServerLifecycleEvents.SERVER_STOPPED.register(listener::accept);
	}

	@Override
	public void onTickEnd(Consumer<MinecraftServer> listener) {
		ServerTickEvents.END_SERVER_TICK.register(listener::accept);
	}

	@Override
	public void onPlayerJoin(BiConsumer<ServerPlayerEntity, MinecraftServer> listener) {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> listener.accept(handler.getPlayer(), server));
	}

	@Override
	public void onPlayerDisconnect(BiConsumer<ServerPlayerEntity, MinecraftServer> listener) {
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> listener.accept(handler.getPlayer(), server));
	}

	@Override
	public void onBlockBreak(BlockBreakCheck check) {
		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) ->
				!(world instanceof ServerWorld serverWorld && player instanceof ServerPlayerEntity serverPlayer)
						|| check.allows(serverWorld, serverPlayer, pos, state));
	}
}
