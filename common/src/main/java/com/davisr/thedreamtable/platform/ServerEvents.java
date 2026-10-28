package com.davisr.thedreamtable.platform;

import net.minecraft.block.BlockState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

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

	@FunctionalInterface
	interface BlockBreakCheck {
		boolean allows(ServerWorld world, ServerPlayerEntity player, BlockPos pos, BlockState state);
	}
}
