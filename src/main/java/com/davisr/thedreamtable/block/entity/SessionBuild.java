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

import static com.davisr.thedreamtable.block.entity.DreamTableBlockEntity.*;

/**
 * A session's build in motion: starting and resuming, placing the blocks due each tick, and every way it stops,
 * each with its message and sound. The state itself lives in {@link BuildProgress}; {@link FarmBuilder} places the blocks.
 */
public final class SessionBuild {
	private static final int MAX_SKIPS_PER_TICK = 4096;

	private final TableSession session;
	private final BuildProgress progress;
	private final SessionAreas areas;
	private int ticks;

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
			areas.recordFinished(world, template);
		}
		if (!progress.is(PHASE_BUILDING) || session.onlineOwner(world) == null) {
			return;
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
	 */
	public boolean begin(ServerPlayerEntity starter, boolean fromScratch) {
		FarmTemplate template = session.template();
		if (template == null || !(session.table().getWorld() instanceof ServerWorld world)) {
			return false;
		}
		if (fromScratch) {
			String problem = areas.problemAt(world, starter, FarmBuilder.site(session), template);
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
		areas.claim(world, template);
		progress.start(session.player(), fromScratch, template.signature());
		session.sync();
		session.tell(Text.translatable("gui.the_dream_table.building", progress.index(), session.totalSteps()));
		return true;
	}

	/** A block could not be placed, or the step failed unexpectedly. Nothing was spent; Try Again repeats the step. */
	public void placementFailed(BlockPos where) {
		stop(PHASE_BLOCKED, Text.translatable("gui.the_dream_table.place_failed", where.getX(), where.getY(), where.getZ()), where);
	}

	/** The owner isn't allowed to build at this spot, by spawn protection, the border, or a claim. Try Again checks again. */
	public void protectedSpot(BlockPos where) {
		stop(PHASE_BLOCKED, Text.translatable("gui.the_dream_table.protected_spot", where.getX(), where.getY(), where.getZ()), where);
	}

	public void obstruct(BlockPos where, BlockState obstacle) {
		stop(PHASE_BLOCKED, Text.translatable("gui.the_dream_table.blocked_at", obstacle.getBlock().getName()), where);
	}

	public void missingMaterial() {
		stop(PHASE_NEED, Text.translatable("gui.the_dream_table.paused"), session.getPos());
	}

	private void stop(String phase, Text message, BlockPos where) {
		progress.stop(phase);
		session.sync();
		session.tell(message);
		session.table().sound(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), where, 0.8F, 0.5F);
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
			areas.recordFinished(world, template);
		}
		session.sync();
		session.tell(Text.translatable("gui.the_dream_table.done"));
		session.table().sound(SoundEvents.BLOCK_BEACON_ACTIVATE, session.getPos(), 0.6F, 1.2F);
	}

	public void advance() {
		progress.advance();
		session.sync();
	}

	/** Marks the current step as placed by the table, and whether it used a delivered item. */
	public void recordPlaced(boolean usedItem) {
		progress.recordPlaced(usedItem);
	}
}
