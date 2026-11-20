package com.davisr.thedreamtable.forge;

import com.davisr.thedreamtable.platform.ServerEvents;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraftforge.common.util.Result;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Forge's server lifecycle, tick and connection events. Forge 61 events carry their own {@code BUS}. */
final class ForgeServerEvents implements ServerEvents {
	@Override
	public void onServerStarting(Consumer<MinecraftServer> listener) {
		// Forge's ServerStartingEvent fires after the worlds load; this one fires before, like Fabric's SERVER_STARTING.
		ServerAboutToStartEvent.BUS.addListener(event -> listener.accept(event.getServer()));
	}

	@Override
	public void onServerStarted(Consumer<MinecraftServer> listener) {
		ServerStartedEvent.BUS.addListener(event -> listener.accept(event.getServer()));
	}

	@Override
	public void onServerStopped(Consumer<MinecraftServer> listener) {
		ServerStoppedEvent.BUS.addListener(event -> listener.accept(event.getServer()));
	}

	@Override
	public void onTickEnd(Consumer<MinecraftServer> listener) {
		TickEvent.ServerTickEvent.Post.BUS.addListener(event -> listener.accept(event.server()));
	}

	@Override
	public void onPlayerJoin(BiConsumer<ServerPlayerEntity, MinecraftServer> listener) {
		PlayerEvent.PlayerLoggedInEvent.BUS.addListener(event -> {
			if (event.getEntity() instanceof ServerPlayerEntity player) {
				listener.accept(player, player.getEntityWorld().getServer());
			}
		});
	}

	@Override
	public void onCommandRegistration(Consumer<CommandDispatcher<ServerCommandSource>> listener) {
		RegisterCommandsEvent.BUS.addListener(event -> listener.accept(event.getDispatcher()));
	}

	@Override
	public void onBlockBreak(BlockBreakCheck check) {
		// Forge 61 stops a break only when the result is DENY. Not canceled, so later listeners still see the event.
		BlockEvent.BreakEvent.BUS.addListener((BlockEvent.BreakEvent event) -> {
			if (event.getLevel() instanceof ServerWorld world && event.getPlayer() instanceof ServerPlayerEntity player
					&& !check.allows(world, player, event.getPos(), event.getState())) {
				event.setResult(Result.DENY);
			}
		});
	}

	@Override
	public void onPlayerDisconnect(BiConsumer<ServerPlayerEntity, MinecraftServer> listener) {
		PlayerEvent.PlayerLoggedOutEvent.BUS.addListener(event -> {
			if (event.getEntity() instanceof ServerPlayerEntity player) {
				listener.accept(player, player.getEntityWorld().getServer());
			}
		});
	}
}
