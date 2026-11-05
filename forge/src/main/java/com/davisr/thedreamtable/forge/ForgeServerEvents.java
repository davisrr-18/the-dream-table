package com.davisr.thedreamtable.forge;

import com.davisr.thedreamtable.platform.ServerEvents;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.IEventBus;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static com.davisr.thedreamtable.forge.ForgePlatform.listen;

/** Forge's server lifecycle, tick and connection events. */
final class ForgeServerEvents implements ServerEvents {
	private static final IEventBus BUS = MinecraftForge.EVENT_BUS;

	@Override
	public void onServerStarting(Consumer<MinecraftServer> listener) {
		// Forge's ServerStartingEvent fires after the worlds load; this one fires before, like Fabric's SERVER_STARTING.
		listen(BUS, ServerAboutToStartEvent.class, event -> listener.accept(event.getServer()));
	}

	@Override
	public void onServerStarted(Consumer<MinecraftServer> listener) {
		listen(BUS, ServerStartedEvent.class, event -> listener.accept(event.getServer()));
	}

	@Override
	public void onServerStopped(Consumer<MinecraftServer> listener) {
		listen(BUS, ServerStoppedEvent.class, event -> listener.accept(event.getServer()));
	}

	@Override
	public void onTickEnd(Consumer<MinecraftServer> listener) {
		listen(BUS, TickEvent.ServerTickEvent.Post.class, event -> listener.accept(event.getServer()));
	}

	@Override
	public void onPlayerJoin(BiConsumer<ServerPlayerEntity, MinecraftServer> listener) {
		listen(BUS, PlayerEvent.PlayerLoggedInEvent.class, event -> {
			if (event.getEntity() instanceof ServerPlayerEntity player) {
				listener.accept(player, player.server);
			}
		});
	}

	@Override
	public void onCommandRegistration(Consumer<CommandDispatcher<ServerCommandSource>> listener) {
		listen(BUS, RegisterCommandsEvent.class, event -> listener.accept(event.getDispatcher()));
	}

	@Override
	public void onBlockBreak(BlockBreakCheck check) {
		listen(BUS, BlockEvent.BreakEvent.class, event -> {
			if (event.getLevel() instanceof ServerWorld world && event.getPlayer() instanceof ServerPlayerEntity player
					&& !check.allows(world, player, event.getPos(), event.getState())) {
				event.setCanceled(true);
			}
		});
	}

	@Override
	public void onPlayerDisconnect(BiConsumer<ServerPlayerEntity, MinecraftServer> listener) {
		listen(BUS, PlayerEvent.PlayerLoggedOutEvent.class, event -> {
			if (event.getEntity() instanceof ServerPlayerEntity player) {
				listener.accept(player, player.server);
			}
		});
	}
}
