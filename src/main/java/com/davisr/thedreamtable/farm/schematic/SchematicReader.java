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

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Turns a schematic file into a farm template, whatever the format, and says what is wrong with it: why it can't be
 * used at all, or what to know before building it.
 */
public final class SchematicReader {
	public static final int MAX_BLOCKS = 16384;
	/** The tallest a world can be: from -64 to 320 in the Overworld. */
	public static final int MAX_HEIGHT = 384;
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
		return fromRoot(id, NbtIo.readCompressed(stream, NbtSizeTracker.ofUnlimitedBytes())).template();
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
			return fromRoot(id, root);
		} catch (NbtSizeValidationException tooLarge) {
			TheDreamTableMod.LOGGER.warn("Schematic {} holds more than {} bytes of data", path.getFileName(), MAX_BYTES);
			return Result.rejected(ImportNote.Kind.TOO_LARGE, 0);
		} catch (IOException | RuntimeException exception) {
			TheDreamTableMod.LOGGER.warn("Could not read schematic {}", path.getFileName(), exception);
			return Result.rejected(ImportNote.Kind.UNREADABLE, 0);
		}
	}

	static Result fromRoot(String id, NbtCompound root) {
		if (root.contains("Schematic", NbtElement.COMPOUND_TYPE)) {
			root = root.getCompound("Schematic");
		}
		StepCollector collector = new StepCollector();
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
		if (steps.isEmpty()) {
			TheDreamTableMod.LOGGER.warn("{} has no blocks to build", id);
			return Result.rejected(ImportNote.Kind.EMPTY, 0);
		}
		if (steps.size() > MAX_BLOCKS) {
			TheDreamTableMod.LOGGER.warn("{} has {} blocks; the limit is {}", id, steps.size(), MAX_BLOCKS);
			return Result.rejected(ImportNote.Kind.TOO_MANY_BLOCKS, steps.size());
		}
		int height = height(steps);
		if (height > MAX_HEIGHT) {
			TheDreamTableMod.LOGGER.warn("{} is {} blocks tall; the most a world allows is {}", id, height, MAX_HEIGHT);
			return Result.rejected(ImportNote.Kind.TOO_TALL, height);
		}
		steps.sort(BUILD_ORDER);
		List<ImportNote> notes = new ArrayList<>();
		if (collector.unknownBlocks() > 0) {
			notes.add(new ImportNote(ImportNote.Kind.UNKNOWN_BLOCKS, collector.unknownBlocks(), first(collector.unknownNames())));
		}
		if (!collector.creativeNames().isEmpty()) {
			notes.add(new ImportNote(ImportNote.Kind.CREATIVE_ONLY, collector.creativeNames().size(), first(collector.creativeNames())));
		}
		return new Result(FarmTemplate.of(id, steps), List.copyOf(notes));
	}

	private static List<String> first(Set<String> names) {
		return names.stream().limit(ImportNote.SHOWN_NAMES).toList();
	}

	private static int height(List<FarmStep> steps) {
		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;
		for (FarmStep step : steps) {
			min = Math.min(min, step.y());
			max = Math.max(max, step.y());
		}
		return max - min + 1;
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
