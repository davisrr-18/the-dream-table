package com.davisr.thedreamtable.forge;

import com.davisr.thedreamtable.TheDreamTableMod;
import com.davisr.thedreamtable.farm.GuardedGoal;
import com.davisr.thedreamtable.platform.ServerEvents;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraftforge.common.util.Result;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
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
	public void onExplosion(ExplosionFilter filter) {
		// Detonate fires after the explosion chose its blocks and before it destroys them; its list is the one the game uses.
		ExplosionEvent.Detonate.BUS.addListener(event -> {
			if (event.getLevel() instanceof ServerWorld world) {
				filter.filter(world, event.getExplosion().getCausingEntity(), event.getAffectedBlocks());
			}
		});
	}

	private static boolean warnedUnguarded;

	@Override
	public void onEndermanGrief(EndermanCheck check) {
		// Forge 61 never asks EntityMobGriefingEvent from the enderman's pickup goal, so the goal itself is swapped for a
		// guarded one when the enderman joins a world. Its goals are rebuilt every time it loads, so each is wrapped once.
		EntityJoinLevelEvent.BUS.addListener((EntityJoinLevelEvent event) -> {
			if (event.getEntity() instanceof EndermanEntity enderman && event.getLevel() instanceof ServerWorld
					&& !GuardedGoal.guard(enderman.goalSelector, ForgeServerEvents::isPickUpGoal,
							() -> !(enderman.getEntityWorld() instanceof ServerWorld world) || check.allows(world, enderman))
					&& !warnedUnguarded) {
				// Once per run: a missing goal (renamed class, or a modpack removed it) would otherwise log on every enderman.
				warnedUnguarded = true;
				TheDreamTableMod.LOGGER.warn("An enderman ({}) has no block pickup goal that Protect My Farms can guard", enderman.getClass().getName());
			}
		});
	}

	/** The pickup goal is a package-private inner class of the enderman, found by name: yarn's in development, Mojang's in the game. */
	private static boolean isPickUpGoal(Goal goal) {
		Class<?> type = goal.getClass();
		return type.getEnclosingClass() == EndermanEntity.class
				&& (type.getSimpleName().equals("PickUpBlockGoal") || type.getSimpleName().equals("EndermanTakeBlockGoal"));
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
