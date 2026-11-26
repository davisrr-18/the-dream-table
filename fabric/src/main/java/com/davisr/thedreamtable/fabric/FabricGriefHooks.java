package com.davisr.thedreamtable.fabric;

import com.davisr.thedreamtable.platform.ServerEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The listeners for explosions and endermen picking up blocks. Fabric API has no event for either, so the mixins in
 * {@code fabric.mixin} call these; the listeners are added at startup and only read afterwards, on the server thread.
 */
public final class FabricGriefHooks {
	private static final List<ServerEvents.ExplosionFilter> EXPLOSION_FILTERS = new ArrayList<>();
	private static final List<ServerEvents.EndermanCheck> ENDERMAN_CHECKS = new ArrayList<>();

	private FabricGriefHooks() {
	}

	static void addExplosionFilter(ServerEvents.ExplosionFilter filter) {
		EXPLOSION_FILTERS.add(filter);
	}

	static void addEndermanCheck(ServerEvents.EndermanCheck check) {
		ENDERMAN_CHECKS.add(check);
	}

	/** Lets the listeners take positions out of an explosion's block list before the blocks are destroyed. */
	public static void filterExplosion(ServerWorld world, @Nullable Entity causingEntity, List<BlockPos> affected) {
		for (ServerEvents.ExplosionFilter filter : EXPLOSION_FILTERS) {
			filter.filter(world, causingEntity, affected);
		}
	}

	/** Whether every listener lets the enderman pick up a block now. */
	public static boolean endermanAllowed(ServerWorld world, EndermanEntity enderman) {
		for (ServerEvents.EndermanCheck check : ENDERMAN_CHECKS) {
			if (!check.allows(world, enderman)) {
				return false;
			}
		}
		return true;
	}
}
