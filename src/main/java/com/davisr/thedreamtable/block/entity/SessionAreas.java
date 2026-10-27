package com.davisr.thedreamtable.block.entity;

import com.davisr.thedreamtable.farm.BuildPermission;
import com.davisr.thedreamtable.farm.FarmAreas;
import com.davisr.thedreamtable.farm.FarmBuilder;
import com.davisr.thedreamtable.farm.FarmGrant;
import com.davisr.thedreamtable.farm.FarmSite;
import com.davisr.thedreamtable.farm.FarmTemplate;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

/**
 * A session's place in {@link FarmAreas}: whether its farm may start at a site, the area its build reserves,
 * and the finished farm it leaves behind.
 */
final class SessionAreas {
	private final TableSession session;
	private boolean finishedRecorded;

	SessionAreas(TableSession session) {
		this.session = session;
	}

	FarmAreas.Key key() {
		return FarmAreas.Key.build(session.getPos(), session.player());
	}

	/**
	 * Why the farm can't start at the site, as a lang key, or null when it can: it leaves the world, overlaps another farm
	 * at any table, or touches land the player may not build in. Build and the hologram check both ask this.
	 */
	String problemAt(ServerWorld world, ServerPlayerEntity asker, FarmSite site, FarmTemplate template) {
		Box area = site.footprint(template);
		if (!site.fits(world, template)) {
			return "gui.the_dream_table.out_of_world";
		}
		if (session.table().overlapsOthers(session, area)) {
			return "gui.the_dream_table.farm_overlaps";
		}
		FarmAreas.Conflict conflict = FarmAreas.of(world).conflict(world, key(), area);
		if (conflict == FarmAreas.Conflict.ACTIVE) {
			return "gui.the_dream_table.farm_overlaps";
		}
		if (conflict == FarmAreas.Conflict.FINISHED) {
			return "gui.the_dream_table.farm_overlaps_finished";
		}
		if (!BuildPermission.canBuildAll(world, asker, site, template)) {
			return "gui.the_dream_table.protected_area";
		}
		return null;
	}

	/** How much of this farm already stands at the site, so a farm built before areas existed can be built over and protected. */
	int existingPercent(ServerWorld world, FarmSite site, FarmTemplate template) {
		return FarmAreas.presence(world, template, site, session.swaps()).percent();
	}

	void claim(ServerWorld world, FarmTemplate template) {
		finishedRecorded = false;
		FarmAreas.of(world).claim(key(), FarmBuilder.site(session).footprint(template));
	}

	/**
	 * A finished farm keeps its area after the session moves on, so no later farm is placed over it or clears its blocks.
	 * Farms finished before areas existed are recorded the first time their table ticks.
	 */
	void recordFinished(ServerWorld world, FarmTemplate template, @Nullable UUID instanceId, Map<UUID, FarmGrant> grants) {
		finishedRecorded = true;
		FarmAreas areas = FarmAreas.of(world);
		if (instanceId == null) {
			areas.recordFinished(session.player(), template, FarmBuilder.site(session), session.swaps());
		} else {
			areas.recordFinished(session.player(), template, FarmBuilder.site(session), session.swaps(), instanceId, grants);
		}
		areas.release(key());
	}

	boolean finishedRecorded() {
		return finishedRecorded;
	}

	void release(ServerWorld world) {
		FarmAreas.of(world).release(key());
	}
}
