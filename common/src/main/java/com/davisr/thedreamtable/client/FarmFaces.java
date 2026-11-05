package com.davisr.thedreamtable.client;

import com.davisr.thedreamtable.TheDreamTableMod;
import com.davisr.thedreamtable.client.hologram.FrameBudget;
import com.davisr.thedreamtable.client.hologram.GhostRenderer;
import com.davisr.thedreamtable.client.hologram.WeightedCache;
import com.davisr.thedreamtable.farm.FarmGeometry;
import com.davisr.thedreamtable.farm.FarmStep;
import com.davisr.thedreamtable.farm.FarmTemplate;
import com.davisr.thedreamtable.platform.Platform;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.EmptyBlockView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * The faces of a farm, worked out once and kept, for the hologram and the miniature, so a frame walks a list
 * instead of looking up every model again. Each face remembers the solid block of the farm that would cover it:
 * the miniature always leaves those faces out, and the hologram does when that block is built or drawn too.
 * The cache is sized by the total number of parts it holds, and the hologram's first build of a farm is spread
 * over frames by a {@link FrameBudget}.
 */
public final class FarmFaces {
	/** The cache keeps this many parts in all (the sum over every kept farm), not a number of farms, so several tables don't push each other out. */
	static final long CACHED_PARTS = 160_000L;
	/** An unfinished build nobody asked for in this many frames is dropped. */
	private static final long ABANDONED_FRAMES = 120L;
	private static final WeightedCache<String, List<Part>> CACHE = new WeightedCache<>(CACHED_PARTS, (key, parts) -> { });
	private static final Map<String, Pending> PENDING = new HashMap<>();
	private static long frame;

	private FarmFaces() {
	}

	/** Kept faces point into the texture atlas, so they are dropped whenever resources reload. */
	public static void register() {
		Platform.clientEvents().onResourcesReloaded(new Identifier(TheDreamTableMod.MOD_ID, "farm_faces"), manager -> {
			CACHE.clear();
			PENDING.clear();
			GhostRenderer.release();
		});
	}

	/** Called once per rendered frame: the cache keeps what the frame used, and abandoned unfinished builds are dropped. */
	public static void beginFrame(long number) {
		frame = number;
		PENDING.values().removeIf(pending -> number - pending.touched > ABANDONED_FRAMES);
	}

	/**
	 * One block to draw: its build step, where it sits, its final state, and its quads with their tints and the step of
	 * the solid block covering each, or -1. Chests and other animated blocks have no quads and are drawn as entities;
	 * {@code fallback} covers blocks without a model.
	 */
	public record Part(int step, BlockPos pos, BlockState state, List<BakedQuad> quads, int[] tints, int[] coveredBy, boolean animated, Sprite fallback) {
		public boolean covered(int quad) {
			return coveredBy[quad] >= 0;
		}
	}

	/** What identifies one set of parts: the farm, its swaps, and how it is turned. */
	public static String key(FarmTemplate template, String swapKey, Direction facing, boolean mirrored) {
		return template.id() + "#" + template.signature() + "#" + swapKey + "#" + (facing == null ? "-" : facing.asString() + (mirrored ? "m" : ""));
	}

	/**
	 * The parts in build order. With a facing (and optionally mirrored), positions and states are turned as the hologram places them around an
	 * anchor at the origin; without one, they stay in the farm's own layout, as the spinning miniature needs.
	 */
	public static List<Part> of(FarmTemplate template, String swapKey, UnaryOperator<BlockState> swap, Direction facing, boolean mirrored) {
		return ready(template, swapKey, swap, facing, mirrored, null);
	}

	/**
	 * Like {@link #of}, but the first build is spread over frames: with a {@code budget} it does only what the frame can
	 * afford and returns null until the parts are done. Without one it builds everything at once.
	 */
	public static List<Part> ready(FarmTemplate template, String swapKey, UnaryOperator<BlockState> swap, Direction facing, boolean mirrored, FrameBudget budget) {
		String key = key(template, swapKey, facing, mirrored);
		List<Part> cached = CACHE.get(key, frame);
		if (cached != null) {
			return cached;
		}
		Pending pending = PENDING.computeIfAbsent(key, ignored -> new Pending(new Builder(template, swap, facing, mirrored)));
		pending.touched = frame;
		if (!pending.builder.advance(budget)) {
			return null;
		}
		PENDING.remove(key);
		List<Part> parts = pending.builder.result();
		CACHE.put(key, parts, parts.size(), frame);
		return parts;
	}

	private static final class Pending {
		private final Builder builder;
		private long touched;

		private Pending(Builder builder) {
			this.builder = builder;
		}
	}

	/** Only the faces nothing in the farm covers, for the miniature, which always shows the whole farm. */
	public static List<Part> outside(FarmTemplate template, String swapKey, UnaryOperator<BlockState> swap) {
		String key = template.id() + "#" + template.signature() + "#" + swapKey + "#outside";
		List<Part> cached = CACHE.get(key, frame);
		if (cached != null) {
			return cached;
		}
		List<Part> all = of(template, swapKey, swap, null, false);
		List<Part> outside = new ArrayList<>();
		for (Part part : all) {
			if (part.animated()) {
				outside.add(part);
				continue;
			}
			List<BakedQuad> quads = new ArrayList<>();
			List<Integer> tints = new ArrayList<>();
			for (int quad = 0; quad < part.quads().size(); quad++) {
				if (!part.covered(quad)) {
					quads.add(part.quads().get(quad));
					tints.add(part.tints()[quad]);
				}
			}
			if (!quads.isEmpty()) {
				int[] none = new int[quads.size()];
				Arrays.fill(none, -1);
				outside.add(new Part(part.step(), part.pos(), part.state(), List.copyOf(quads), tints.stream().mapToInt(Integer::intValue).toArray(), none, false, part.fallback()));
			}
		}
		List<Part> kept = List.copyOf(outside);
		CACHE.put(key, kept, kept.size(), frame);
		return kept;
	}

	/**
	 * Works out the parts of one farm in slices, so a big farm can be spread over several frames: first where every step
	 * sits and which blocks are solid, then each step's model faces.
	 */
	private static final class Builder {
		private static final int COLLECT_SLICE = 256;
		private static final int PARTS_SLICE = 24;

		private final FarmTemplate template;
		private final UnaryOperator<BlockState> swap;
		private final Direction facing;
		private final boolean mirrored;
		private final List<FarmStep> steps;
		private final BlockPos[] positions;
		private final Map<Long, BlockState> states = new HashMap<>();
		private final Map<Long, Integer> solid = new HashMap<>();
		private final List<Part> parts = new ArrayList<>();
		private final Random random = Random.create();
		private boolean collecting = true;
		private int cursor;
		private List<Part> result;

		private Builder(FarmTemplate template, UnaryOperator<BlockState> swap, Direction facing, boolean mirrored) {
			this.template = template;
			this.swap = swap;
			this.facing = facing;
			this.mirrored = mirrored;
			this.steps = template.steps();
			this.positions = new BlockPos[steps.size()];
		}

		/** Does at least one slice, then more while the budget allows; true once every part is worked out. */
		private boolean advance(FrameBudget budget) {
			MinecraftClient client = MinecraftClient.getInstance();
			do {
				if (collecting) {
					int end = Math.min(steps.size(), cursor + COLLECT_SLICE);
					for (; cursor < end; cursor++) {
						collect(cursor);
					}
					if (cursor >= steps.size()) {
						collecting = false;
						cursor = 0;
					}
				} else {
					int end = Math.min(steps.size(), cursor + PARTS_SLICE);
					for (; cursor < end; cursor++) {
						part(client, cursor);
					}
					if (cursor >= steps.size()) {
						result = List.copyOf(parts);
						return true;
					}
				}
			} while (budget == null || !budget.spent());
			return false;
		}

		private List<Part> result() {
			return result;
		}

		private void collect(int index) {
			FarmStep step = steps.get(index);
			if (step.isEntity() || step.state() == null) {
				return;
			}
			BlockState state = swap.apply(step.state());
			BlockPos pos;
			if (facing == null) {
				pos = new BlockPos(step.x(), step.y(), step.z());
			} else {
				state = FarmGeometry.orient(state, facing, mirrored);
				pos = FarmGeometry.toAimed(BlockPos.ORIGIN, facing, mirrored, template, step);
			}
			positions[index] = pos;
			states.put(pos.asLong(), state);
			if (state.isOpaqueFullCube(EmptyBlockView.INSTANCE, BlockPos.ORIGIN)) {
				solid.put(pos.asLong(), index);
			}
		}

		private void part(MinecraftClient client, int index) {
			BlockPos pos = positions[index];
			if (pos == null) {
				return;
			}
			BlockState state = states.get(pos.asLong());
			BlockRenderType type = state.getRenderType();
			if (type == BlockRenderType.INVISIBLE) {
				return;
			}
			BlockColors colors = client.getBlockColors();
			BakedModel model = client.getBlockRenderManager().getModel(state);
			if (type == BlockRenderType.ENTITYBLOCK_ANIMATED) {
				parts.add(new Part(index, pos, state, List.of(), new int[0], new int[0], true, model.getParticleSprite()));
				return;
			}
			List<BakedQuad> quads = new ArrayList<>();
			List<Integer> covers = new ArrayList<>();
			for (Direction side : Direction.values()) {
				random.setSeed(pos.asLong());
				List<BakedQuad> faces = model.getQuads(state, side, random);
				int cover = solid.getOrDefault(pos.offset(side).asLong(), -1);
				quads.addAll(faces);
				faces.forEach(face -> covers.add(cover));
			}
			random.setSeed(pos.asLong());
			List<BakedQuad> loose = model.getQuads(state, null, random);
			quads.addAll(loose);
			loose.forEach(face -> covers.add(-1));
			int[] tints = new int[quads.size()];
			int[] coveredBy = new int[quads.size()];
			for (int quad = 0; quad < quads.size(); quad++) {
				tints[quad] = tint(colors, state, quads.get(quad));
				coveredBy[quad] = covers.get(quad);
			}
			parts.add(new Part(index, pos, state, List.copyOf(quads), tints, coveredBy, false, model.getParticleSprite()));
		}
	}

	/** Grass, leaves and the like are tinted with their default colors, since a preview has no biome. */
	private static int tint(BlockColors colors, BlockState state, BakedQuad quad) {
		if (!quad.hasColor()) {
			return -1;
		}
		try {
			return colors.getColor(state, null, null, quad.getColorIndex());
		} catch (RuntimeException noDefault) {
			return -1;
		}
	}
}
