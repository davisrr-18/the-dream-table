package com.davisr.thedreamtable.platform;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.List;
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

	/** When a player has joined and can receive payloads. */
	void onPlayerJoin(BiConsumer<ServerPlayerEntity, MinecraftServer> listener);

	/** When a player leaves the server; the player is still readable. */
	void onPlayerDisconnect(BiConsumer<ServerPlayerEntity, MinecraftServer> listener);

	/**
	 * Before a player breaks a block on the server; returning false cancels the break. It also runs when
	 * {@link BlockBreakGuard} asks the loader whether a player may break a block, so it must be side-effect free
	 * apart from telling the player.
	 */
	void onBlockBreak(BlockBreakCheck check);

	/**
	 * Before an explosion on the server destroys blocks; the listener removes from the mutable {@code affected} list the
	 * positions that must survive. {@code causingEntity} is the entity that caused it (a creeper, a TNT's igniter) or null.
	 * It must be side-effect free.
	 */
	void onExplosion(ExplosionFilter filter);

	/**
	 * Each time an enderman is about to pick up a block on the server; returning false stops it this time. It must be
	 * side-effect free and cheap: Forge asks it on every goal check of an empty-handed enderman, before the game's
	 * random roll, so about once a tick per enderman; Fabric only when the game would start the pickup.
	 */
	void onEndermanGrief(EndermanCheck check);

	/**
	 * When the server builds its command tree (at startup and again on {@code /reload}); the listener adds the mod's commands
	 * to the dispatcher. It runs for dedicated and integrated servers alike.
	 */
	void onCommandRegistration(Consumer<CommandDispatcher<ServerCommandSource>> listener);

	@FunctionalInterface
	interface BlockBreakCheck {
		boolean allows(ServerWorld world, ServerPlayerEntity player, BlockPos pos, BlockState state);
	}

	@FunctionalInterface
	interface ExplosionFilter {
		void filter(ServerWorld world, @Nullable Entity causingEntity, List<BlockPos> affected);
	}

	@FunctionalInterface
	interface EndermanCheck {
		boolean allows(ServerWorld world, EndermanEntity enderman);
	}
}
