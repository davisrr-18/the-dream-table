package com.davisr.thedreamtable.farm;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import org.jetbrains.annotations.Nullable;

/**
 * Walks the cells of one footprint layer for the clear that runs before the layer's first step. It holds the cursor of
 * the layer being cleared, the step cells the clear must leave alone, and the cells looked at this tick, so a large footprint
 * is spread over many ticks. Built once per build and kept in memory: the saved progress records only the layers finished.
 */
public final class LayerClear {
	/** Cells looked at per tick across all layers of one build, so clearing never stalls the server. */
	public static final int CELLS_PER_TICK = 256;

	private final int minX;
	private final int minY;
	private final int minZ;
	private final int width;
	private final int depth;
	private final LongOpenHashSet steps;
	private int layer;
	/** Index of the next cell in the layer, counted x first; -1 when no layer is being cleared. */
	private int cursor = -1;
	private long budgetTime = Long.MIN_VALUE;
	private int spent;

	LayerClear(int minX, int minY, int minZ, int width, int depth, LongOpenHashSet steps) {
		this.minX = minX;
		this.minY = minY;
		this.minZ = minZ;
		this.width = width;
		this.depth = depth;
		this.steps = steps;
	}

	/** The footprint of a build at its site, and every cell a step places or spawns in. */
	public static LayerClear of(FarmTemplate template, FarmSite site) {
		Box box = site.footprint(template);
		LongOpenHashSet steps = new LongOpenHashSet();
		for (FarmStep step : template.steps()) {
			steps.add(site.target(template, step).asLong());
		}
		return new LayerClear((int) box.minX, (int) box.minY, (int) box.minZ, (int) (box.maxX - box.minX), (int) (box.maxZ - box.minZ), steps);
	}

	/** The layer of a cell: its height above the footprint's lowest block. */
	public int layerOf(BlockPos pos) {
		return pos.getY() - minY;
	}

	public boolean isStepCell(BlockPos pos) {
		return steps.contains(pos.asLong());
	}

	/** Starts clearing a layer, unless this layer is already under way and keeps its cursor. */
	public void start(int layer) {
		if (cursor < 0 || this.layer != layer) {
			this.layer = layer;
			cursor = 0;
		}
	}

	/** The next cell of the layer to look at, or null when no layer is being cleared or every cell has been looked at. */
	public @Nullable BlockPos current() {
		if (cursor < 0 || cursor >= width * depth) {
			return null;
		}
		return new BlockPos(minX + cursor / depth, minY + layer, minZ + cursor % depth);
	}

	/** Whether this tick can look at another cell. The budget restarts on each new world tick. */
	public boolean hasBudget(long time) {
		if (time != budgetTime) {
			budgetTime = time;
			spent = 0;
		}
		return spent < CELLS_PER_TICK;
	}

	/** Moves past the current cell, which counts against this tick's budget. */
	public void advance() {
		cursor++;
		spent++;
	}

	/** The layer has been cleared, or the clear was dropped. */
	public void finish() {
		cursor = -1;
	}
}
