package com.davisr.thedreamtable.farm.schematic;

import com.davisr.thedreamtable.TheDreamTableMod;
import com.davisr.thedreamtable.farm.FarmStep;
import com.davisr.thedreamtable.farm.FarmTemplate;
import com.davisr.thedreamtable.farm.ImportNote;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.PlantBlock;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.nbt.NbtSizeValidationException;
import net.minecraft.util.math.BlockPos;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Turns a schematic file into a farm template, whatever the format, and says what is wrong with it: why it can't be
 * used at all, or what to know before building it.
 */
public final class SchematicReader {
	public static final int MAX_BLOCKS = 16384;
	/** The most cells all regions of a file may declare, walked or not; far above any farm's bounding box, far below what takes a server's time. */
	public static final long MAX_CELLS = 1L << 25;
	/** The most palette entries a file may declare. */
	public static final int MAX_PALETTE = 1 << 17;
	/** The tallest a world can be: from -64 to 320 in the Overworld. */
	public static final int MAX_HEIGHT = 384;
	/** The longest a file may be on any axis, for every format; far past any farm, and keeps every coordinate far inside the int range. */
	public static final int MAX_SPAN = 512;
	/** The most data a file may hold, so a huge or crafted file can't take the server's memory. Farms within the block limit need far less. */
	public static final long MAX_BYTES = 64L * 1024 * 1024;

	/** The farm, null when the file can't be used, and the notes: the reason it can't, or warnings. */
	public record Result(FarmTemplate template, List<ImportNote> notes) {
		static Result rejected(ImportNote.Kind kind, int count) {
			return new Result(null, List.of(ImportNote.of(kind, count)));
		}
	}

	/** Solid blocks first, then liquids, then plants, then entities; within each, bottom to top. */
	private static final Comparator<FarmStep> BUILD_ORDER = Comparator
			.comparingInt((FarmStep step) -> step.isEntity() ? 1 : 0)
			.thenComparingInt(SchematicReader::priority)
			.thenComparingInt(FarmStep::y)
			.thenComparingInt(FarmStep::x)
			.thenComparingInt(FarmStep::z);

	private SchematicReader() {
	}

	/** A bundled farm; null when it can't be used. */
	public static FarmTemplate read(String id, InputStream stream) throws IOException {
		return fromRoot(id, NbtIo.readCompressed(stream, NbtSizeTracker.ofUnlimitedBytes()), false).template();
	}

	/** A player's file. Never throws: a file that can't be read becomes an {@link ImportNote.Kind#UNREADABLE} result, logged with its cause. */
	public static Result read(String id, Path path) {
		try {
			if (Files.size(path) > MAX_BYTES) {
				return Result.rejected(ImportNote.Kind.TOO_LARGE, 0);
			}
			NbtCompound root;
			try {
				root = NbtIo.readCompressed(path, NbtSizeTracker.of(MAX_BYTES));
			} catch (IOException compressed) {
				try (DataInputStream input = new DataInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
					root = NbtIo.readCompound(input, NbtSizeTracker.of(MAX_BYTES));
				}
			}
			return fromRoot(id, root, true);
		} catch (NbtSizeValidationException tooLarge) {
			TheDreamTableMod.LOGGER.warn("Schematic {} holds more than {} bytes of data", path.getFileName(), MAX_BYTES);
			return Result.rejected(ImportNote.Kind.TOO_LARGE, 0);
		} catch (IOException | RuntimeException exception) {
			TheDreamTableMod.LOGGER.warn("Could not read schematic {}", path.getFileName(), exception);
			return Result.rejected(ImportNote.Kind.UNREADABLE, 0);
		}
	}

	/** @param stripContents drops the items held by block entities, true for a player's file and false for bundled farms. */
	static Result fromRoot(String id, NbtCompound root, boolean stripContents) {
		if (root.contains("Schematic", NbtElement.COMPOUND_TYPE)) {
			root = root.getCompound("Schematic");
		}
		StepCollector collector = new StepCollector(stripContents);
		if (LitematicaReader.accepts(root)) {
			LitematicaReader.read(root, collector);
		} else if (SpongeReader.accepts(root)) {
			SpongeReader.read(root, collector);
		} else if (VanillaReader.accepts(root)) {
			VanillaReader.read(root, collector);
		} else {
			TheDreamTableMod.LOGGER.warn("{} has no block NBT the table can build", id);
			return Result.rejected(ImportNote.Kind.UNKNOWN_FORMAT, 0);
		}
		List<FarmStep> steps = collector.steps();
		if (collector.widest() > MAX_SPAN) {
			TheDreamTableMod.LOGGER.warn("{} is {} blocks long on one side; the limit is {}", id, collector.widest(), MAX_SPAN);
			return Result.rejected(ImportNote.Kind.TOO_WIDE, (int) Math.min(Integer.MAX_VALUE, collector.widest()));
		}
		if (collector.total() > MAX_BLOCKS) {
			TheDreamTableMod.LOGGER.warn("{} has {} blocks or cells; the limit is {}", id, collector.total(), MAX_BLOCKS);
			return Result.rejected(ImportNote.Kind.TOO_MANY_BLOCKS, collector.total());
		}
		if (steps.isEmpty()) {
			TheDreamTableMod.LOGGER.warn("{} has no blocks to build", id);
			return Result.rejected(ImportNote.Kind.EMPTY, 0);
		}
		// The real extent of the steps, whatever the header said; longs because the coordinates of a crafted file can span the whole int range.
		long[] extent = extent(steps);
		if (extent[1] > MAX_HEIGHT) {
			TheDreamTableMod.LOGGER.warn("{} is {} blocks tall; the most a world allows is {}", id, extent[1], MAX_HEIGHT);
			return Result.rejected(ImportNote.Kind.TOO_TALL, (int) Math.min(Integer.MAX_VALUE, extent[1]));
		}
		long wide = Math.max(extent[0], extent[2]);
		if (wide > MAX_SPAN) {
			TheDreamTableMod.LOGGER.warn("{} is {} blocks long on one side; the limit is {}", id, wide, MAX_SPAN);
			return Result.rejected(ImportNote.Kind.TOO_WIDE, (int) Math.min(Integer.MAX_VALUE, wide));
		}
		steps.sort(BUILD_ORDER);
		FarmTemplate template;
		try {
			template = FarmTemplate.of(id, steps);
		} catch (IllegalStateException conflict) {
			// Said in the file's own words to the player; the cause is in the log without a stack trace.
			ImportNote.Kind kind = overlaps(steps) ? ImportNote.Kind.OVERLAP : ImportNote.Kind.TOO_MANY_MATERIALS;
			TheDreamTableMod.LOGGER.warn("{} can't be built: {}", id, conflict.getMessage());
			return Result.rejected(kind, 0);
		}
		List<ImportNote> notes = new ArrayList<>();
		if (collector.tableBlocks() > 0) {
			notes.add(ImportNote.of(ImportNote.Kind.TABLE_BLOCKS, collector.tableBlocks()));
		}
		if (collector.unknownBlocks() > 0) {
			notes.add(new ImportNote(ImportNote.Kind.UNKNOWN_BLOCKS, collector.unknownBlocks(), first(collector.unknownNames())));
		}
		if (!collector.creativeNames().isEmpty()) {
			notes.add(new ImportNote(ImportNote.Kind.CREATIVE_ONLY, collector.creativeNames().size(), first(collector.creativeNames())));
		}
		return new Result(template, List.copyOf(notes));
	}

	private static List<String> first(Set<String> names) {
		return names.stream().limit(ImportNote.SHOWN_NAMES).toList();
	}

	/** How many blocks the steps cover on x, y and z. */
	private static long[] extent(List<FarmStep> steps) {
		long[] min = {Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE};
		long[] max = {Long.MIN_VALUE, Long.MIN_VALUE, Long.MIN_VALUE};
		for (FarmStep step : steps) {
			int[] at = {step.x(), step.y(), step.z()};
			for (int axis = 0; axis < 3; axis++) {
				min[axis] = Math.min(min[axis], at[axis]);
				max[axis] = Math.max(max[axis], at[axis]);
			}
		}
		return new long[]{max[0] - min[0] + 1, max[1] - min[1] + 1, max[2] - min[2] + 1};
	}

	/** True when two block steps share a spot, the other thing besides too many materials that {@link FarmTemplate#of} refuses. */
	private static boolean overlaps(List<FarmStep> steps) {
		Set<Long> occupied = new HashSet<>();
		for (FarmStep step : steps) {
			if (!step.isEntity() && !occupied.add(BlockPos.asLong(step.x(), step.y(), step.z()))) {
				return true;
			}
		}
		return false;
	}

	private static int priority(FarmStep step) {
		if (step.state() == null) {
			return 0;
		}
		Block block = step.state().getBlock();
		if (block == Blocks.WATER || block == Blocks.LAVA) {
			return 2;
		}
		if (block instanceof PlantBlock || block == Blocks.SUGAR_CANE || block == Blocks.BAMBOO || block == Blocks.BAMBOO_SAPLING || block == Blocks.CACTUS) {
			return 3;
		}
		return 0;
	}
}
