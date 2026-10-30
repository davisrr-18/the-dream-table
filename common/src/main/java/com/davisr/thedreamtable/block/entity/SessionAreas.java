package com.davisr.thedreamtable.block.entity;

import com.davisr.thedreamtable.TheDreamTableMod;
import com.davisr.thedreamtable.farm.BuildPermission;
import com.davisr.thedreamtable.farm.FarmAccess;
import com.davisr.thedreamtable.farm.FarmAction;
import com.davisr.thedreamtable.farm.FarmDirectory;
import com.davisr.thedreamtable.farm.FarmOptions;
import com.davisr.thedreamtable.farm.FarmAreas;
import com.davisr.thedreamtable.farm.FarmBuilder;
import com.davisr.thedreamtable.farm.FarmSite;
import com.davisr.thedreamtable.farm.FarmTemplate;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import org.jetbrains.annotations.Nullable;

import java.util.BitSet;
import java.util.UUID;

/**
 * A session's place in {@link FarmAreas}: whether its farm may start at a site, the area its build reserves,
 * and the finished farm it leaves behind.
 */
final class SessionAreas {
	private final TableSession session;
	private final BuildProgress progress;

	SessionAreas(TableSession session, BuildProgress progress) {
		this.session = session;
		this.progress = progress;
	}

	FarmAreas.Key key() {
		return FarmAreas.Key.build(session.getPos(), session.player());
	}

	/**
	 * Why the farm can't start at the site, as a lang key, or null when it can: it is in a dimension the farm is not made for, it leaves the world, overlaps another farm
	 * at any table, or touches land the player may not build in. Build and the hologram check both ask this.
	 */
	String problemAt(ServerWorld world, ServerPlayerEntity asker, FarmSite site, FarmTemplate template) {
		return problemAt(world, asker, site, template, null);
	}

	String problemAt(ServerWorld world, ServerPlayerEntity asker, FarmSite site, FarmTemplate template, @Nullable UUID ignoredFinished) {
		Box area = site.footprint(template);
		if (FarmOptions.byId(session.getSelectedFarm()).filter(option -> option.outsideIntendedDimension(world.getRegistryKey())).isPresent()) {
			return "gui.the_dream_table.wrong_dimension";
		}
		if (!site.fits(world, template)) {
			return "gui.the_dream_table.out_of_world";
		}
		if (session.table().overlapsOthers(session, area)) {
			return "gui.the_dream_table.farm_overlaps";
		}
		FarmAreas.Conflict conflict = FarmAreas.of(world).conflict(world, key(), area, ignoredFinished);
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

	/** Whether this session's farm already has a finished record at its spot. */
	boolean recordedAtSite(ServerWorld world) {
		return FarmAreas.of(world).recordedAt(session.player(), FarmBuilder.site(session));
	}

	/** What stands at this session's site now. */
	FarmAreas.Presence presenceAtSite(ServerWorld world, FarmTemplate template) {
		return FarmAreas.presence(world, template, FarmBuilder.site(session), session.swaps());
	}

	void claim(ServerWorld world, FarmTemplate template) {
		progress.setFinishedRecorded(false);
		FarmAreas.of(world).claim(key(), FarmBuilder.site(session).footprint(template));
	}

	/**
	 * A finished farm keeps its area after the session moves on, so no later farm is placed over it or clears its blocks.
	 * The session remembers it has recorded, so a farm released since is not recorded again after a reload. Farms finished
	 * before areas existed are recorded the first time their table ticks, when the session was saved by a version that never did.
	 */
	void recordFinished(ServerWorld world, FarmTemplate template, @Nullable UUID instanceId, @Nullable String customName,
			BitSet placed, BitSet paid) {
		progress.setFinishedRecorded(true);
		FarmAreas areas = FarmAreas.of(world);
		if (instanceId == null) {
			areas.recordFinished(session.player(), template, FarmBuilder.site(session), session.swaps(), UUID.randomUUID(), null, placed, paid);
		} else {
			areas.recordFinished(session.player(), template, FarmBuilder.site(session), session.swaps(), instanceId, customName, placed, paid);
		}
		areas.release(key());
	}

	void completeRepair(ServerWorld world, RepairContext context, BitSet placed, BitSet paid) {
		progress.setFinishedRecorded(true);
		FarmAreas areas = FarmAreas.of(world);
		if (!areas.completeRepair(context.farmKey().instanceId(), context.operationId(), placed, paid)) {
			// The lease was reconciled away while the repair ran. The blocks it placed stand, so the record still gets them.
			TheDreamTableMod.LOGGER.warn("Repair lease of farm {} was gone at completion; merging its placed steps directly", context.farmKey().instanceId());
			areas.mergePlacement(context.farmKey().instanceId(), placed, paid);
		}
		areas.release(key());
	}

	boolean repairActive(ServerWorld world, RepairContext context) {
		return FarmAreas.of(world).repairActive(context.farmKey().instanceId(),
				new com.davisr.thedreamtable.farm.RepairLease(context.operationId(), context.actorUuid(), session.getPos()));
	}

	/** Whether the repair's actor still may repair the farm: its owner, so a repair stops if the farm is no longer theirs. */
	boolean repairPermitted(ServerWorld world, RepairContext context) {
		return FarmDirectory.resolve(world.getServer(), context.farmKey())
				.map(located -> FarmAccess.allows(located.record(), context.actorUuid(), FarmAction.REPAIR))
				.orElse(false);
	}

	boolean finishedRecorded() {
		return progress.finishedRecorded();
	}

	void release(ServerWorld world) {
		FarmAreas areas = FarmAreas.of(world);
		RepairContext context = session.repairContext();
		if (context != null) {
			areas.failRepair(context.farmKey().instanceId(), context.operationId());
		}
		areas.release(key());
	}
}
