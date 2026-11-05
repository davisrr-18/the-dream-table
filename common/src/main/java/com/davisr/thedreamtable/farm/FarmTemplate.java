package com.davisr.thedreamtable.farm;

import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A farm as an ordered list of steps plus the materials they add up to.
 * {@code center} is the footprint middle on x and z (rounded down) and the lowest y; the farm is anchored by it when aimed.
 * {@code mirrorSum} is the lowest plus the highest step x: a block at x lands at {@code mirrorSum - x} when the farm is
 * mirrored, so the mirrored footprint covers exactly the cells of the plain one even when the width is even. It is
 * derived from the steps, so it never has to be stored.
 */
public record FarmTemplate(String id, List<FarmStep> steps, List<MaterialSpec> materials, BlockPos center, int mirrorSum) {
	public static final int MAX_SLOTS = 54;

	public FarmTemplate(String id, List<FarmStep> steps, List<MaterialSpec> materials, BlockPos center) {
		this(id, steps, materials, center, mirrorSumOf(steps));
	}

	public static FarmTemplate of(String id, List<FarmStep> steps) {
		Map<Item, Integer> totals = new LinkedHashMap<>();
		Set<Long> plants = new HashSet<>();
		for (FarmStep step : steps) {
			if (grows(step)) {
				plants.add(BlockPos.asLong(step.x(), step.y(), step.z()));
			}
		}
		for (FarmStep step : steps) {
			boolean growth = grows(step) && plants.contains(BlockPos.asLong(step.x(), step.y() - 1, step.z()));
			// Water stays a material (water buckets): a nearby infinite source covers it instead of the delivered buckets, see FarmBuilder.supply.
			if (step.item() != null && !growth) {
				totals.merge(step.item(), 1, Integer::sum);
			}
		}
		List<MaterialSpec> materials = new ArrayList<>();
		for (Map.Entry<Item, Integer> entry : totals.entrySet()) {
			materials.add(new MaterialSpec(entry.getKey(), entry.getValue()));
		}
		if (materials.size() > MAX_SLOTS) {
			throw new IllegalStateException(id + " needs " + materials.size() + " material slots");
		}
		Set<Long> occupied = new HashSet<>();
		List<String> overlaps = new ArrayList<>();
		for (FarmStep step : steps) {
			if (step.isEntity()) {
				continue;
			}
			long key = BlockPos.asLong(step.x(), step.y(), step.z());
			if (!occupied.add(key)) {
				overlaps.add(step.x() + ", " + step.y() + ", " + step.z());
			}
		}
		if (!overlaps.isEmpty()) {
			throw new IllegalStateException(id + " places two blocks at " + String.join("; ", overlaps));
		}
		return new FarmTemplate(id, List.copyOf(steps), List.copyOf(materials), centerOf(steps));
	}

	/**
	 * A fingerprint of every step: position, block state, item and entity. A build remembers it, so a farm
	 * whose file changed since the build started is noticed before its saved progress is used on the wrong steps.
	 */
	public long signature() {
		return SIGNATURES.get(this, FarmTemplate::computeSignature);
	}

	/** The steps never change, so the fingerprint is worked out once per template instead of on every frame the hologram asks. */
	private static final IdentityCache<FarmTemplate, Long> SIGNATURES = new IdentityCache<>();

	private static long computeSignature(FarmTemplate template) {
		long hash = 1125899906842597L;
		List<FarmStep> steps = template.steps;
		for (FarmStep step : steps) {
			hash = 31 * hash + BlockPos.asLong(step.x(), step.y(), step.z());
			hash = 31 * hash + (step.state() == null ? 0 : step.state().toString().hashCode());
			hash = 31 * hash + (step.item() == null ? 0 : Registries.ITEM.getId(step.item()).hashCode());
			hash = 31 * hash + (step.entity() == null ? 0 : Registries.ENTITY_TYPE.getId(step.entity()).hashCode());
		}
		return hash == 0 ? 1 : hash;
	}

	/** Plant segments that grow back by themselves are not asked for; see {@link FarmGrowth}. */
	private static boolean grows(FarmStep step) {
		return !step.isEntity() && step.state() != null && (step.state().isOf(Blocks.SUGAR_CANE) || step.state().isOf(Blocks.CACTUS)
				|| step.state().isOf(Blocks.BAMBOO) || step.state().isOf(Blocks.BAMBOO_SAPLING)
				|| step.state().isOf(Blocks.KELP) || step.state().isOf(Blocks.KELP_PLANT));
	}

	private static int mirrorSumOf(List<FarmStep> steps) {
		int minX = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		for (FarmStep step : steps) {
			minX = Math.min(minX, step.x());
			maxX = Math.max(maxX, step.x());
		}
		return steps.isEmpty() ? 0 : minX + maxX;
	}

	private static BlockPos centerOf(List<FarmStep> steps) {
		int minX = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int minY = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxZ = Integer.MIN_VALUE;
		for (FarmStep step : steps) {
			minX = Math.min(minX, step.x());
			maxX = Math.max(maxX, step.x());
			minY = Math.min(minY, step.y());
			minZ = Math.min(minZ, step.z());
			maxZ = Math.max(maxZ, step.z());
		}
		if (steps.isEmpty()) {
			return BlockPos.ORIGIN;
		}
		return new BlockPos(Math.floorDiv(minX + maxX, 2), minY, Math.floorDiv(minZ + maxZ, 2));
	}
}
