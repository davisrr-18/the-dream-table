package com.davisr.thedreamtable.client;

import com.davisr.thedreamtable.block.DreamTableBlock;
import com.davisr.thedreamtable.block.entity.DreamTableBlockEntity;
import com.davisr.thedreamtable.block.entity.TableSession;
import com.davisr.thedreamtable.farm.BuildPace;
import com.davisr.thedreamtable.platform.Platform;
import com.davisr.thedreamtable.screen.page.TimeText;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * The live "Building 12/340 · ~4 min" line in the notice slot while the local player's own build or repair runs at a table
 * whose chunk is loaded on the client. Built only from what the client already has: the player's own session that the
 * server pushes with every placed block ({@code SessionSyncPayload}). It is shown while the build runs and gone when it
 * finishes, pauses, stops or is discarded, when the player walks out of the table's chunk, and when the world changes;
 * the table menu hides it because its Work page shows the same progress.
 */
public final class BuildProgressLine {
	private static BlockPos watched;
	/** The build's actual blocks per second as the open table menu last reported it (0 when unknown); it counts the server's limit and Creative. */
	private static int rate;
	private static BlockPos rateTable;

	private BuildProgressLine() {
	}

	public static void register() {
		Platform.clientEvents().onTickEnd(BuildProgressLine::tick);
	}

	/** What the client sees of a watched table. */
	record Snapshot(boolean chunkLoaded, boolean tableStanding, boolean ownSession, String phase, int index, int total, int blocksPerSecond) {
	}

	/** What to do with the watched table this tick. */
	enum Step {
		/** The build runs here and is in view: show the line. */
		SHOW,
		/** Nothing to see for now (chunk away, session not yet sent) but the build may still be running: keep watching. */
		HIDE,
		/** The build is over, stopped, discarded or the table is gone: stop watching. */
		FORGET
	}

	static Step step(Snapshot view) {
		if (!view.chunkLoaded() || !view.ownSession()) {
			return Step.HIDE;
		}
		if (!view.tableStanding() || !DreamTableBlockEntity.PHASE_BUILDING.equals(view.phase())) {
			return Step.FORGET;
		}
		return view.total() > 0 && view.index() >= 0 ? Step.SHOW : Step.HIDE;
	}

	/** "Building 12/340", with the time left at the build's rate when it is known. */
	static Text text(int index, int total, int blocksPerSecond) {
		Text status = Text.translatable("gui.the_dream_table.building", index, total);
		if (blocksPerSecond <= 0) {
			return status;
		}
		return Text.translatable("gui.the_dream_table.with_eta", status, TimeText.of(BuildPace.secondsLeftAt(blocksPerSecond, total, index)));
	}

	/** A session arrived for a table: if it is building, follow that table. */
	static void observe(BlockPos pos, TableSession session) {
		if (DreamTableBlockEntity.PHASE_BUILDING.equals(session.getPhase())) {
			watched = pos.toImmutable();
		}
	}

	/** Remembers the rate the table menu shows for this table, for the time estimate. */
	public static void noteRate(BlockPos table, int blocksPerSecond) {
		rateTable = table.toImmutable();
		rate = blocksPerSecond;
	}

	/** The table whose session the client should have, so a returning player gets the line without opening the menu. */
	static @Nullable BlockPos watched() {
		return watched;
	}

	static void clear() {
		watched = null;
		rate = 0;
		rateTable = null;
		TableNotices.setProgress(null);
	}

	private static void tick(MinecraftClient client) {
		if (watched == null) {
			return;
		}
		if (client.world == null || client.player == null) {
			clear();
			return;
		}
		boolean loaded = DreamTableHologram.chunkLoaded(client.world, watched);
		BlockEntity entity = loaded ? client.world.getBlockEntity(watched) : null;
		boolean standing = loaded && client.world.getBlockState(watched).getBlock() instanceof DreamTableBlock;
		Snapshot view = new Snapshot(loaded, standing, false, "", 0, 0, 0);
		if (entity instanceof DreamTableBlockEntity table && table.hasOwnSession()) {
			TableSession session = table.session(client.player);
			view = new Snapshot(loaded, standing, true, session.getPhase(), session.getBuildIndex(), session.totalSteps(), watched.equals(rateTable) ? rate : 0);
		}
		Step step = step(view);
		TableNotices.setProgress(step == Step.SHOW ? text(view.index(), view.total(), view.blocksPerSecond()) : null);
		if (step == Step.FORGET) {
			watched = null;
		}
	}
}
