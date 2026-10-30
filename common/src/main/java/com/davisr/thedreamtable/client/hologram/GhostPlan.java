package com.davisr.thedreamtable.client.hologram;

/**
 * How a farm's parts, in build order, are split for drawing. Parts are grouped in fixed chunks of {@link #CHUNK};
 * a chunk that holds only unbuilt parts is baked once into a GPU buffer and redrawn until the visible layer window
 * changes. The chunk that holds the next part to build is the "live" one and is drawn directly every frame, since its
 * parts change as blocks are placed and the next one has its own tint. Chunks before it are fully built and skipped.
 */
public final class GhostPlan {
	/** Parts per baked chunk. Small enough that the live chunk is cheap to draw directly, big enough to keep draw calls few. */
	public static final int CHUNK = 128;

	/** What to do with a chunk this frame. */
	public enum Kind {
		/** Every part is already built. */
		BUILT,
		/** Holds the next part to build: drawn directly. */
		LIVE,
		/** Every part is above the visible layers. */
		HIDDEN,
		/** Drawn from its baked buffer. */
		BAKED
	}

	private final int[] partStep;
	private final int[] partY;
	private final int[] chunkMinY;
	private final int maxY;

	/**
	 * @param partStep the build step of each part, ascending
	 * @param partY the template layer of each part
	 */
	public GhostPlan(int[] partStep, int[] partY) {
		if (partStep.length != partY.length) {
			throw new IllegalArgumentException("one layer per part");
		}
		this.partStep = partStep.clone();
		this.partY = partY.clone();
		int chunks = chunkCount(partStep.length);
		this.chunkMinY = new int[chunks];
		int highest = Integer.MIN_VALUE;
		for (int chunk = 0; chunk < chunks; chunk++) {
			int low = Integer.MAX_VALUE;
			for (int part = chunkStart(chunk); part < chunkEnd(chunk); part++) {
				low = Math.min(low, partY[part]);
				highest = Math.max(highest, partY[part]);
			}
			chunkMinY[chunk] = low;
		}
		this.maxY = highest;
	}

	public static int chunkCount(int parts) {
		return (parts + CHUNK - 1) / CHUNK;
	}

	public int parts() {
		return partStep.length;
	}

	public int chunks() {
		return chunkMinY.length;
	}

	public int chunkStart(int chunk) {
		return chunk * CHUNK;
	}

	public int chunkEnd(int chunk) {
		return Math.min(partStep.length, (chunk + 1) * CHUNK);
	}

	public int step(int part) {
		return partStep[part];
	}

	public int layer(int part) {
		return partY[part];
	}

	/** The highest layer any part sits on. */
	public int maxLayer() {
		return maxY;
	}

	/** The index of the first part whose step is at or after {@code shownPlaced}; {@link #parts()} when all are built. */
	public int firstUnbuilt(int shownPlaced) {
		int low = 0;
		int high = partStep.length;
		while (low < high) {
			int middle = (low + high) >>> 1;
			if (partStep[middle] < shownPlaced) {
				low = middle + 1;
			} else {
				high = middle;
			}
		}
		return low;
	}

	/** The layer window to bake for: never above the farm's own top, so every larger window shares one set of buffers. */
	public int window(int topLayer) {
		return Math.min(topLayer, maxY);
	}

	/** What to do with {@code chunk} when parts before {@code firstUnbuilt} are built and layers up to {@code topLayer} show. */
	public Kind kind(int chunk, int firstUnbuilt, int topLayer) {
		if (chunkEnd(chunk) <= firstUnbuilt) {
			return Kind.BUILT;
		}
		if (chunk == firstUnbuilt / CHUNK) {
			return Kind.LIVE;
		}
		return chunkMinY[chunk] > topLayer ? Kind.HIDDEN : Kind.BAKED;
	}
}
