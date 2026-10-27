package com.davisr.thedreamtable.block.entity;

import com.davisr.thedreamtable.DreamTableConfig;
import com.davisr.thedreamtable.farm.BuildPace;
import com.davisr.thedreamtable.farm.ChunkKeeper;
import com.davisr.thedreamtable.farm.FarmBuilder;
import com.davisr.thedreamtable.farm.FarmTemplate;
import net.minecraft.block.BlockState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

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
		if (progress.is(PHASE_DONE) && template != null && !areas.finishedRecorded()) {
			complete(world, template);
		}
		if (!progress.is(PHASE_BUILDING)) {
			return;
		}
		RepairContext repair = progress.repairContext();
		if (repair != null && !areas.repairActive(world, repair)) {
			stop(PHASE_BLOCKED, new BuildIssue(BuildIssue.Kind.ERROR, session.getPos(), "repair_lease", 1));
			return;
		}
		if (session.onlineOwner(world) == null) {
			ownerAway = true;
			return;
		}
		if (ownerAway) {
			ownerAway = false;
			session.tell(Text.translatable("gui.the_dream_table.build_continues", progress.index(), session.totalSteps()));
		}
		if (ticks++ % BuildPace.TICKS_PER_SECOND == 0) {
			ChunkKeeper.keepTable(world, session.getPos());
		}
		int due = progress.due(blocksPerSecond());
		int placed = 0;
		int skipped = 0;
		while (placed < due && skipped < MAX_SKIPS_PER_TICK && progress.is(PHASE_BUILDING)) {
			FarmBuilder.Outcome outcome = FarmBuilder.placeNext(session, world, placed == 0);
			if (outcome == FarmBuilder.Outcome.STOPPED) {
				break;
			}
			if (outcome == FarmBuilder.Outcome.PLACED) {
				placed++;
			} else {
				skipped++;
			}
		}
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
		if (template == null || !(session.table().getWorld() instanceof ServerWorld world)) {
			return false;
		}
		if (fromScratch) {
			String problem = areas.problemAt(world, starter, FarmBuilder.site(session), template,
					repair == null ? null : repair.farmKey().instanceId());
			if (problem != null) {
				session.clearPlacement();
				starter.sendMessage(Text.translatable(problem), true);
				return false;
			}
		}
		if (!fromScratch && !progress.matches(template.signature())) {
			starter.sendMessage(Text.translatable("gui.the_dream_table.farm_changed"), true);
			return false;
		}
		BuildIssue previous = fromScratch ? null : progress.issue();
		if (previous != null && !previous.temporary()) {
			BuildIssue now = FarmBuilder.check(session, world);
			if (now != null) {
				progress.setIssue(now);
				session.sync();
				session.tell(Text.translatable("gui.the_dream_table.still_blocked", now.what()));
				session.table().sound(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), now.where(), 0.8F, 0.5F);
				return false;
			}
		}
		areas.claim(world, template);
		progress.start(session.player(), fromScratch, template.signature());
		if (repair != null) {
			progress.startRepair(repair);
		}
		waitingSince = -1;
		session.sync();
		String started = previous != null && !previous.temporary() ? "gui.the_dream_table.issue_solved" : "gui.the_dream_table.building";
		session.tell(Text.translatable(started, progress.index(), session.totalSteps()));
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
			session.tell(issue.what());
		}
	}

	private void stop(String phase, BuildIssue issue) {
		progress.stop(phase);
		progress.setIssue(issue);
		session.sync();
		session.tell(issue.what());
		session.table().sound(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), issue.where(), 0.8F, 0.5F);
	}

	/** Stops after the current step; nothing is undone and resuming continues from the next block. */
	public boolean pause() {
		if (!progress.is(PHASE_BUILDING)) {
			return false;
		}
		progress.stop(PHASE_PAUSED);
		session.sync();
		session.tell(Text.translatable("gui.the_dream_table.paused_build", progress.index(), session.totalSteps()));
		return true;
	}

	public void finish() {
		progress.stop(PHASE_DONE);
		FarmTemplate template = session.template();
		if (template != null && session.table().getWorld() instanceof ServerWorld world) {
			complete(world, template);
		}
		session.sync();
		Text done = !progress.repair() ? Text.translatable("gui.the_dream_table.done")
				: progress.kept() > 0 ? Text.translatable("gui.the_dream_table.repair_done_kept", progress.kept())
				: Text.translatable("gui.the_dream_table.repair_done");
		session.tell(done);
		session.table().sound(SoundEvents.BLOCK_BEACON_ACTIVATE, session.getPos(), 0.6F, 1.2F);
	}

	private void complete(ServerWorld world, FarmTemplate template) {
		RepairContext context = progress.repairContext();
		if (context == null) {
			areas.recordFinished(world, template, progress.repairInstanceId(), progress.repairCustomName(), progress.repairGrants(), progress.repairInvites());
		} else {
			areas.completeRepair(world, context);
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
}
