package com.davisr.thedreamtable.farm;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Plants a farm harvests and that grow back by themselves: sugar cane, bamboo, cactus and kelp. A segment standing on
 * another segment of the same plant is growth, not part of the build: it is never placed, never counted as missing,
 * and never repaired, since the farm breaks it on purpose. The plant's lowest block still is.
 */
public final class FarmGrowth {
	/**
	 * The growth steps of each template, found once per template object. The cache is keyed by identity because
	 * {@link FarmTemplate} is a record: its own hash covers every step and would be recomputed on each lookup.
	 */
	private static final IdentityCache<FarmTemplate, BitSet> TOPS = new IdentityCache<>();

	private FarmGrowth() {
	}

	/** True when this step is a plant segment standing on the same plant in the farm's own layout. */
	public static boolean isGrowth(FarmTemplate template, int step) {
		return growth(template).get(step);
	}

	/**
	 * The growth steps of a template, computed once. The set is shared and must not be changed; loops over many steps
	 * should fetch it once instead of calling {@link #isGrowth} per step.
	 */
	public static BitSet growth(FarmTemplate template) {
		return TOPS.get(template, FarmGrowth::tops);
	}

	private static BitSet tops(FarmTemplate template) {
		Map<Long, Block> plants = new HashMap<>();
		for (FarmStep step : template.steps()) {
			Block plant = plant(step);
			if (plant != null) {
				plants.put(BlockPos.asLong(step.x(), step.y(), step.z()), plant);
			}
		}
		BitSet tops = new BitSet(template.steps().size());
		for (int index = 0; index < template.steps().size(); index++) {
			FarmStep step = template.steps().get(index);
			Block plant = plant(step);
			if (plant != null && plant == plants.get(BlockPos.asLong(step.x(), step.y() - 1, step.z()))) {
				tops.set(index);
			}
		}
		return tops;
	}

	/** The plant a step belongs to, with a bamboo shoot counted as bamboo and kelp's body as kelp; null for anything else. */
	private static Block plant(FarmStep step) {
		if (step.isEntity() || step.state() == null) {
			return null;
		}
		BlockState state = step.state();
		if (state.isOf(Blocks.SUGAR_CANE) || state.isOf(Blocks.CACTUS)) {
			return state.getBlock();
		}
		if (state.isOf(Blocks.BAMBOO) || state.isOf(Blocks.BAMBOO_SAPLING)) {
			return Blocks.BAMBOO;
		}
		if (state.isOf(Blocks.KELP) || state.isOf(Blocks.KELP_PLANT)) {
			return Blocks.KELP;
		}
		return null;
	}
}
