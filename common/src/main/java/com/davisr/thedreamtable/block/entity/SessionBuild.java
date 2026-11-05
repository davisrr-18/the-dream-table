package com.davisr.thedreamtable.block.entity;

import com.davisr.thedreamtable.network.TableNoticePayload;
import com.davisr.thedreamtable.DreamTableConfig;
import com.davisr.thedreamtable.farm.BuildPace;
import com.davisr.thedreamtable.farm.ChunkKeeper;
import com.davisr.thedreamtable.farm.FarmBuilder;
import com.davisr.thedreamtable.farm.FarmSite;
import com.davisr.thedreamtable.farm.FarmTemplate;
import com.davisr.thedreamtable.farm.FarmAreas;
import com.davisr.thedreamtable.farm.LayerClear;
import com.davisr.thedreamtable.farm.alert.FarmAlertType;
import net.minecraft.block.BlockState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.BitSet;
import java.util.UUID;

import static com.davisr.thedreamtable.block.entity.DreamTableBlockEntity.*;

/**
 * A session's build in motion: starting and resuming, placing the blocks due each tick, and every way it stops,
 * each with its message and sound. The state itself lives in {@link BuildProgress}; {@link FarmBuilder} places the blocks.
 */
public final class SessionBuild {
	private static final int MAX_SKIPS_PER_TICK = 4096;
	/** A chunk that takes longer than this to load is shown as the reason the build waits. */
	private static final long CHUNK_WAIT_TICKS = 100;

	private final TableSession session;
	private final BuildProgress progress;
	private final SessionAreas areas;
	private int ticks;
	private long waitingSince = -1;
	private int failedStep = -1;
	private int failures;
	/** The build waited for its owner to come back online. Not saved: after a restart everyone rejoins anyway. */
	private boolean ownerAway;
	/** The design instance last found to match the signature this build started with, so the signature isn't recomputed every tick. */
	private @Nullable FarmTemplate verified;
	/** The layer clearing of this build, made on first use and dropped when a build starts from scratch. Not saved. */
	private @Nullable LayerClear clearing;

	SessionBuild(TableSession session, BuildProgress progress, SessionAreas areas) {
		this.session = session;
		this.progress = progress;
		this.areas = areas;
	}

	/**
	 * Places the blocks due this tick. Blocks already in place are skipped without using the pace,
	 * up to {@link #MAX_SKIPS_PER_TICK}, so resuming a half-built farm doesn't wait on them.
	 * While building, the table's chunk is kept loaded so the build goes on when the owner walks away.
	 */
	void tick(ServerWorld world) {
		FarmTemplate template = session.template();
		if (progress.is(PHASE_DONE) && template != null) {
			if (!areas.finishedRecorded()) {
				complete(world, template, false);
			} else if (progress.legacyDone() && world.getTime() % LEGACY_CHECK_TICKS == 0) {
				settleLegacy(world, template);
			}
		}
		if (endRepairIfInvalid(world, world.getTime() % BuildPace.TICKS_PER_SECOND == 0) || !progress.is(PHASE_BUILDING)) {
			return;
		}
		if (!stillSameFarm(template)) {
			return;
		}
		if (session.onlineOwner(world) == null) {
			ownerAway = true;
			return;
		}
		if (ownerAway) {
			ownerAway = false;
			session.tell(Text.translatable("gui.the_dream_table.build_continues", progress.index(), session.totalSteps()), TableNoticePayload.Severity.INFO);
		}
		if (ticks++ % BuildPace.TICKS_PER_SECOND == 0) {
			ChunkKeeper.keepTable(world, session.getPos());
		}
		int due = progress.due(blocksPerSecond());
		int placed = 0;
		int skipped = 0;
		while (placed < due && skipped < MAX_SKIPS_PER_TICK && progress.is(PHASE_BUILDING)) {
			FarmBuilder.Outcome outcome = FarmBuilder.placeNext(session, world, placed == 0);
			if (outcome == FarmBuilder.Outcome.STOPPED || outcome == FarmBuilder.Outcome.CLEARING) {
				break;
			}
			if (outcome == FarmBuilder.Outcome.PLACED) {
				placed++;
			} else {
				skipped++;
			}
		}
	}

	/**
	 * Whether the farm this build started with is still the one it would place. A farm file that was deleted or changed
	 * since stops the build with a message, instead of placing the new design from the old step with the old records;
	 * resuming then goes through {@link #begin}, which checks again.
	 */
	private boolean stillSameFarm(@Nullable FarmTemplate template) {
		boolean matches = template != null && (template == verified || progress.matches(template.signature()));
		if (continueDecision(template != null, matches) == ContinueDecision.CONTINUE) {
			verified = template;
			return true;
		}
		verified = null;
		stop(PHASE_BLOCKED, BuildIssue.at(BuildIssue.Kind.FARM_CHANGED, session.getPos()));
		return false;
	}

	/** Whether a build in motion may go on. */
	enum ContinueDecision {
		CONTINUE, STOP
	}

	/** A build goes on only while its farm exists and still matches the signature it started with. */
	static ContinueDecision continueDecision(boolean templatePresent, boolean signatureMatches) {
		return templatePresent && signatureMatches ? ContinueDecision.CONTINUE : ContinueDecision.STOP;
	}

	/** What a repair's lease and permission check decides. */
	enum RepairCheck {
		/** No repair, or one that holds no lease in this phase. */
		NONE,
		KEEP,
		/** The persisted lease is gone: it was reconciled away or released elsewhere. */
		END_LOST,
		/** The actor may no longer repair the farm, since it is no longer the farm's owner. */
		END_REVOKED
	}

	/**
	 * A repair holds its farm's lease while it builds and while it is paused, blocked or waiting for materials, so all of
	 * those phases are checked, not only the one that places blocks. {@code permitted} only matters on the ticks that look
	 * at the permission; the caller passes true on the others.
	 */
	static RepairCheck repairCheck(String phase, boolean hasRepair, boolean leaseActive, boolean permitted) {
		boolean holdsLease = PHASE_BUILDING.equals(phase) || PHASE_PAUSED.equals(phase) || PHASE_BLOCKED.equals(phase) || PHASE_NEED.equals(phase);
		if (!hasRepair || !holdsLease) {
			return RepairCheck.NONE;
		}
		if (!leaseActive) {
			return RepairCheck.END_LOST;
		}
		return permitted ? RepairCheck.KEEP : RepairCheck.END_REVOKED;
	}

	/**
	 * Ends the session's repair when its lease is gone or its actor may no longer repair the farm. The lease is checked
	 * every tick while building; the other phases, and the permission, are checked when {@code roleDue} (once a second, and on
	 * resume). Returns true when the repair ended.
	 */
	private boolean endRepairIfInvalid(ServerWorld world, boolean roleDue) {
		RepairContext repair = progress.repairContext();
		if (repair == null || !progress.busy() || !roleDue && !progress.is(PHASE_BUILDING)) {
			return false;
		}
		RepairCheck check = repairCheck(progress.phase(), true, areas.repairActive(world, repair), !roleDue || areas.repairPermitted(world, repair));
		if (check == RepairCheck.END_LOST) {
			session.cancelRepair(world, "gui.the_dream_table.repair_ended_lost");
			return true;
		}
		if (check == RepairCheck.END_REVOKED) {
			session.cancelRepair(world, "gui.the_dream_table.repair_ended_revoked");
			return true;
		}
		return false;
	}

	public boolean canResume() {
		return progress.resumable();
	}

	/** The chosen speed level, as stored by {@link BuildPace}. */
	public int pace() {
		return progress.pace();
	}

	public void setPace(int pace) {
		progress.setPace(pace);
		session.sync();
	}

	/** The actual rate: the chosen level of the server's fastest rate, or the fastest rate itself when the owner is in Creative. */
	public int blocksPerSecond() {
		int fastest = DreamTableConfig.maxBlocksPerSecond();
		if (session.table().getWorld() instanceof ServerWorld world) {
			ServerPlayerEntity owner = session.onlineOwner(world);
			if (owner != null && owner.isCreative()) {
				return fastest;
			}
		}
		return BuildPace.blocksPerSecond(progress.pace(), fastest);
	}

	/**
	 * Starts or resumes the build. It refuses, and tells the player why, when a new build can't start where it was placed,
	 * or when a resumed build belongs to a farm file that has changed. A started build reserves its area.
	 * Resuming after a cause the player must fix checks it first: if it is still there the build stays stopped and says
	 * so, without trying again; if it is gone, the player hears that it was solved.
	 */
	public boolean begin(ServerPlayerEntity starter, boolean fromScratch) {
		return begin(starter, fromScratch, null);
	}

	private boolean begin(ServerPlayerEntity starter, boolean fromScratch, @Nullable RepairContext repair) {
		FarmTemplate template = session.template();
		if (!(session.table().getWorld() instanceof ServerWorld world) || session.returnPending(starter)) {
			return false;
		}
		if (template == null) {
			if (!fromScratch) {
				TableNoticePayload.problem(starter, Text.translatable("gui.the_dream_table.farm_changed"));
			}
			return false;
		}
		if (!fromScratch && endRepairIfInvalid(world, true)) {
			return false;
		}
		if (fromScratch) {
			String problem = areas.problemAt(world, starter, FarmBuilder.site(session), template,
					repair == null ? null : repair.farmKey().instanceId());
			if (problem != null) {
				session.clearPlacement();
				TableNoticePayload.problem(starter, Text.translatable(problem));
				return false;
			}
		}
		if (!fromScratch && !progress.matches(template.signature())) {
			TableNoticePayload.problem(starter, Text.translatable("gui.the_dream_table.farm_changed"));
			return false;
		}
		if (!fromScratch && session.movedBuild()) {
			String problem = session.movedBuildProblem(world, starter, template);
			if (problem != null) {
				TableNoticePayload.problem(starter, Text.translatable(problem));
				return false;
			}
		}
		BuildIssue previous = fromScratch ? null : progress.issue();
		if (previous != null && !previous.temporary()) {
			BuildIssue now = FarmBuilder.check(session, world);
			if (now != null) {
				progress.setIssue(now);
				session.sync();
				session.tell(Text.translatable("gui.the_dream_table.still_blocked", now.what()), TableNoticePayload.Severity.PROBLEM);
				session.table().sound(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), now.where(), 0.8F, 0.5F);
				return false;
			}
		}
		session.reconcileMaterials(starter);
		areas.claim(world, template);
		session.settleMovedBuild();
		verified = null;
		progress.start(session.player(), fromScratch, template.signature());
		session.clearAim();
		if (fromScratch) {
			clearing = null;
		}
		if (repair != null) {
			progress.startRepair(repair);
		}
		RepairContext activeRepair = repair != null ? repair : progress.repairContext();
		if (activeRepair != null) {
			FarmAreas.of(world).resolveRepairAlerts(activeRepair.farmKey().instanceId(), System.currentTimeMillis());
		}
		waitingSince = -1;
		session.sync();
		// A fresh start needs no notice: the client shows the live "Building" progress line from the first synced block.
		if (previous != null && !previous.temporary()) {
			session.tell(Text.translatable("gui.the_dream_table.issue_solved", progress.index(), session.totalSteps()), TableNoticePayload.Severity.INFO);
		}
		return true;
	}

	/** Starts a repair: a new build that leaves alone the blocks the player changed and counts them. */
	public boolean beginRepair(ServerPlayerEntity starter, RepairContext context) {
		return begin(starter, true, context);
	}

	public boolean repairing() {
		return progress.repair();
	}

	public @Nullable RepairContext repairContext() {
		return progress.repairContext();
	}

	/** A repair found another block where the farm has one, and leaves it as the player made it. */
	public void keepChanged() {
		progress.keep();
	}

	/** Whether a dropped handover should be reported, at most once during this build or repair. */
	public boolean shouldNotifyDropped(boolean dropped) {
		return progress.shouldNotifyDropped(dropped);
	}

	/** Why the build stopped or waits, or null. */
	public @Nullable BuildIssue issue() {
		return progress.issue();
	}

	/**
	 * The game refused a block, or the step failed unexpectedly. Nothing was spent; Try Again repeats the step.
	 * Failing again at the same step marks it as repeated, so the player is told to look at the spot.
	 */
	public void placementFailed(BlockPos where, BlockState block) {
		stop(PHASE_BLOCKED, BuildIssue.of(BuildIssue.Kind.PLACE_FAILED, where, block.getBlock()).again(countFailure()));
	}

	/** An unexpected error at the current step. Its details are in the log. */
	public void failed(BlockPos where) {
		stop(PHASE_BLOCKED, new BuildIssue(BuildIssue.Kind.ERROR, where.toImmutable(), Integer.toString(progress.index() + 1), countFailure()));
	}

	private int countFailure() {
		if (failedStep != progress.index()) {
			failedStep = progress.index();
			failures = 0;
		}
		return ++failures;
	}

	/** Something the player must deal with stands in the way: a protected spot, a block the table won't clear, or a missing item. */
	public void stopFor(BuildIssue issue) {
		stop(issue.kind() == BuildIssue.Kind.MATERIAL ? PHASE_NEED : PHASE_BLOCKED, issue);
	}

	/**
	 * The next spot's chunk isn't loaded yet. The build keeps waiting there; when that takes long, the player is told
	 * once where, and the message clears when the build moves on.
	 */
	public void waitingFor(ServerWorld world, BlockPos where) {
		long now = world.getTime();
		if (waitingSince < 0) {
			waitingSince = now;
		} else if (now - waitingSince >= CHUNK_WAIT_TICKS && progress.issue() == null) {
			BuildIssue issue = BuildIssue.at(BuildIssue.Kind.UNLOADED, where);
			progress.setIssue(issue);
			session.sync();
			session.tell(issue.what(), TableNoticePayload.Severity.PROBLEM);
		}
	}

	private void stop(String phase, BuildIssue issue) {
		progress.stop(phase);
		progress.setIssue(issue);
		recordRepairAlert(issue.kind() == BuildIssue.Kind.ERROR ? FarmAlertType.REPAIR_FAILED : FarmAlertType.REPAIR_BLOCKED);
		session.sync();
		session.tell(issue.what(), TableNoticePayload.Severity.PROBLEM);
		session.table().sound(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), issue.where(), 0.8F, 0.5F);
	}

	/** Stops after the current step; nothing is undone and resuming continues from the next block. */
	public boolean pause() {
		if (!progress.is(PHASE_BUILDING)) {
			return false;
		}
		progress.stop(PHASE_PAUSED);
		session.sync();
		session.tell(Text.translatable("gui.the_dream_table.paused_build", progress.index(), session.totalSteps()), TableNoticePayload.Severity.INFO);
		return true;
	}

	public void finish() {
		progress.stop(PHASE_DONE);
		FarmTemplate template = session.template();
		if (template != null && session.table().getWorld() instanceof ServerWorld world) {
			complete(world, template, false);
		}
		session.sync();
		Text done = !progress.repair() ? Text.translatable("gui.the_dream_table.done")
				: progress.kept() > 0 ? Text.translatable("gui.the_dream_table.repair_done_kept", progress.kept())
				: Text.translatable("gui.the_dream_table.repair_done");
		session.tell(done, TableNoticePayload.Severity.INFO);
		session.table().sound(SoundEvents.BLOCK_BEACON_ACTIVATE, session.getPos(), 0.6F, 1.2F);
	}

	private void recordRepairAlert(FarmAlertType type) {
		RepairContext context = progress.repairContext();
		if (context != null && session.table().getWorld() instanceof ServerWorld world) {
			FarmAreas.of(world).observeAlert(context.farmKey().instanceId(), type, 1, System.currentTimeMillis());
		}
	}

	/** How often a legacy DONE session whose farm is partly unloaded looks again. */
	private static final long LEGACY_CHECK_TICKS = 100;

	/** What to do with a DONE session saved before the recorded flag existed. */
	enum LegacyDecision {
		RECORD, SKIP, WAIT
	}

	/**
	 * Old DONE sessions were never recorded. Record the farm only when it still physically stands (at least half its
	 * blocks as built, as {@link FarmAreas} counts a standing farm) and has no record; a taken-down farm stays gone.
	 * Parts in unloaded chunks can't be judged, so the decision waits.
	 */
	static LegacyDecision legacyDecision(boolean recordExists, FarmAreas.Presence presence) {
		if (recordExists) {
			return LegacyDecision.SKIP;
		}
		if (presence.unloaded()) {
			return LegacyDecision.WAIT;
		}
		return presence.percent() >= 50 ? LegacyDecision.RECORD : LegacyDecision.SKIP;
	}

	private void settleLegacy(ServerWorld world, FarmTemplate template) {
		LegacyDecision decision = legacyDecision(areas.recordedAtSite(world), areas.presenceAtSite(world, template));
		if (decision == LegacyDecision.WAIT) {
			return;
		}
		progress.settleLegacy();
		if (decision == LegacyDecision.RECORD) {
			complete(world, template, true);
		}
	}

	private void complete(ServerWorld world, FarmTemplate template, boolean legacy) {
		RepairContext context = progress.repairContext();
		UUID repairInstanceId = progress.repairInstanceId();
		if (context == null) {
			BitSet placed = progress.placedSteps();
			BitSet paid = progress.paidSteps();
			if (legacy && placed.isEmpty()) {
				// Nothing was kept of what this old build placed, so every step counts as both, like any record saved before them.
				placed.set(0, template.steps().size());
				paid.set(0, template.steps().size());
			}
			areas.recordFinished(world, template, progress.repairInstanceId(), progress.repairCustomName(), placed, paid);
			if (repairInstanceId != null) {
				FarmAreas farmAreas = FarmAreas.of(world);
				long time = System.currentTimeMillis();
				farmAreas.resolveRepairAlerts(repairInstanceId, time);
				farmAreas.observeAlert(repairInstanceId, FarmAlertType.REPAIR_COMPLETED, 1, time);
			}
		} else {
			FarmAreas farmAreas = FarmAreas.of(world);
			long time = System.currentTimeMillis();
			farmAreas.resolveRepairAlerts(context.farmKey().instanceId(), time);
			areas.completeRepair(world, context, progress.placedSteps(), progress.paidSteps());
			farmAreas.observeAlert(context.farmKey().instanceId(), FarmAlertType.REPAIR_COMPLETED, 1, time);
		}
	}

	public void advance() {
		progress.advance();
		progress.setIssue(null);
		waitingSince = -1;
		session.sync();
	}

	/** Marks the current step as placed by the table, and whether it used a delivered item. */
	public void recordPlaced(boolean usedItem) {
		progress.recordPlaced(usedItem);
	}

	/** The layer clearing of this build's footprint, made on first use. */
	public LayerClear layerClear(FarmTemplate template, FarmSite site) {
		if (clearing == null) {
			clearing = LayerClear.of(template, site);
		}
		return clearing;
	}

	/** Whether the layer was cleared before its first step, or the save predates layer clearing. */
	public boolean layerCleared(int layer) {
		return progress.layerCleared(layer);
	}

	/** Records a layer as cleared once every block of it inside the footprint has been looked at. */
	public void markLayerCleared(int layer) {
		progress.markLayerCleared(layer);
		session.sync();
	}
}
