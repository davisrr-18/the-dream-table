package com.davisr.thedreamtable.farm.schematic;

import com.davisr.thedreamtable.TheDreamTableMod;
import com.davisr.thedreamtable.farm.FarmStep;
import com.davisr.thedreamtable.farm.FarmTemplate;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.PlantBlock;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/** Turns a schematic file into a farm template, whatever the format. */
public final class SchematicReader {
	public static final int MAX_BLOCKS = 16384;

	/** Solid blocks first, then liquids, then plants, then entities; within each, bottom to top. */
	private static final Comparator<FarmStep> BUILD_ORDER = Comparator
			.comparingInt((FarmStep step) -> step.isEntity() ? 1 : 0)
			.thenComparingInt(SchematicReader::priority)
			.thenComparingInt(FarmStep::y)
			.thenComparingInt(FarmStep::x)
			.thenComparingInt(FarmStep::z);

	private SchematicReader() {
	}

	public static FarmTemplate read(String id, InputStream stream) throws IOException {
		return fromRoot(id, NbtIo.readCompressed(stream, NbtSizeTracker.ofUnlimitedBytes()));
	}

	public static FarmTemplate read(String id, Path path) throws IOException {
		NbtCompound root;
		try {
			root = NbtIo.readCompressed(path, NbtSizeTracker.ofUnlimitedBytes());
		} catch (IOException compressed) {
			root = NbtIo.read(path);
		}
		return fromRoot(id, root);
	}

	private static FarmTemplate fromRoot(String id, NbtCompound root) {
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
			return null;
		}
		List<FarmStep> steps = collector.steps();
		if (steps.isEmpty()) {
			TheDreamTableMod.LOGGER.warn("{} has no blocks to build", id);
			return null;
		}
		if (steps.size() > MAX_BLOCKS) {
			TheDreamTableMod.LOGGER.warn("{} has {} blocks; the limit is {}", id, steps.size(), MAX_BLOCKS);
			return null;
		}
		steps.sort(BUILD_ORDER);
		return FarmTemplate.of(id, steps);
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
