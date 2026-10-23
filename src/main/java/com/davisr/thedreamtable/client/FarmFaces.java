package com.davisr.thedreamtable.client;

import com.davisr.thedreamtable.TheDreamTableMod;
import com.davisr.thedreamtable.farm.FarmGeometry;
import com.davisr.thedreamtable.farm.FarmStep;
import com.davisr.thedreamtable.farm.FarmTemplate;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.texture.Sprite;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.EmptyBlockView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * The faces of a farm, worked out once and kept, for the hologram and the miniature, so a frame walks a list
 * instead of looking up every model again. Each face remembers the solid block of the farm that would cover it:
 * the miniature always leaves those faces out, and the hologram does when that block is built or drawn too.
 */
public final class FarmFaces {
	private static final int CACHED = 8;
	private static final Map<String, List<Part>> CACHE = new LinkedHashMap<>(16, 0.75F, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, List<Part>> eldest) {
			return size() > CACHED;
		}
	};

	private FarmFaces() {
	}

	/** Kept faces point into the texture atlas, so they are dropped whenever resources reload. */
	public static void register() {
		ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public Identifier getFabricId() {
				return Identifier.of(TheDreamTableMod.MOD_ID, "farm_faces");
			}

			@Override
			public void reload(ResourceManager manager) {
				CACHE.clear();
			}
		});
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

	/**
	 * The parts in build order. With a facing, positions and states are turned as the hologram places them around an
	 * anchor at the origin; without one, they stay in the farm's own layout, as the spinning miniature needs.
	 */
	public static List<Part> of(FarmTemplate template, String swapKey, UnaryOperator<BlockState> swap, Direction facing) {
		String key = template.id() + "#" + template.signature() + "#" + swapKey + "#" + (facing == null ? "-" : facing.asString());
		return CACHE.computeIfAbsent(key, ignored -> build(template, swap, facing));
	}

	/** Only the faces nothing in the farm covers, for the miniature, which always shows the whole farm. */
	public static List<Part> outside(FarmTemplate template, String swapKey, UnaryOperator<BlockState> swap) {
		String key = template.id() + "#" + template.signature() + "#" + swapKey + "#outside";
		List<Part> cached = CACHE.get(key);
		if (cached != null) {
			return cached;
		}
		List<Part> all = of(template, swapKey, swap, null);
		return CACHE.computeIfAbsent(key, ignored -> {
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
			return List.copyOf(outside);
		});
	}

	private static List<Part> build(FarmTemplate template, UnaryOperator<BlockState> swap, Direction facing) {
		Map<Long, BlockState> states = new HashMap<>();
		List<FarmStep> steps = template.steps();
		BlockPos[] positions = new BlockPos[steps.size()];
		Map<Long, Integer> solid = new HashMap<>();
		for (int index = 0; index < steps.size(); index++) {
			FarmStep step = steps.get(index);
			if (step.isEntity() || step.state() == null) {
				continue;
			}
			BlockState state = swap.apply(step.state());
			BlockPos pos;
			if (facing == null) {
				pos = new BlockPos(step.x(), step.y(), step.z());
			} else {
				state = state.rotate(FarmGeometry.rotation(facing));
				pos = FarmGeometry.toAimed(BlockPos.ORIGIN, facing, template, step);
			}
			positions[index] = pos;
			states.put(pos.asLong(), state);
			if (state.isOpaqueFullCube(EmptyBlockView.INSTANCE, BlockPos.ORIGIN)) {
				solid.put(pos.asLong(), index);
			}
		}
		MinecraftClient client = MinecraftClient.getInstance();
		BlockColors colors = client.getBlockColors();
		Random random = Random.create();
		List<Part> parts = new ArrayList<>();
		for (int index = 0; index < steps.size(); index++) {
			BlockPos pos = positions[index];
			if (pos == null) {
				continue;
			}
			BlockState state = states.get(pos.asLong());
			BlockRenderType type = state.getRenderType();
			if (type == BlockRenderType.INVISIBLE) {
				continue;
			}
			BakedModel model = client.getBlockRenderManager().getModel(state);
			if (type == BlockRenderType.ENTITYBLOCK_ANIMATED) {
				parts.add(new Part(index, pos, state, List.of(), new int[0], new int[0], true, model.getParticleSprite()));
				continue;
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
		return List.copyOf(parts);
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
