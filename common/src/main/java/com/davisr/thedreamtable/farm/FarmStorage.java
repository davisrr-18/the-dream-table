package com.davisr.thedreamtable.farm;

import net.minecraft.block.BarrelBlock;
import net.minecraft.block.Block;
import net.minecraft.block.ChestBlock;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Where a finished farm stands right now: unloaded, running, or running with every chest and barrel full. */
public final class FarmStorage {
	public static final int UNLOADED = 0;
	public static final int RUNNING = 1;
	public static final int FULL = 2;

	private FarmStorage() {
	}

	public static int status(ServerWorld world, FarmTemplate template, FarmSite site) {
		BlockPos middle = site.anchor() != null ? site.anchor() : site.table();
		if (!world.isChunkLoaded(middle) || !world.shouldTickEntity(middle)) {
			return UNLOADED;
		}
		return template != null && full(world, template, site) ? FULL : RUNNING;
	}

	/** True when the farm has storage and all of it is full. A part in an unloaded chunk counts as not full. */
	private static boolean full(ServerWorld world, FarmTemplate template, FarmSite site) {
		boolean any = false;
		for (FarmStep step : template.steps()) {
			if (step.isEntity() || step.state() == null || !storage(step.state().getBlock())) {
				continue;
			}
			BlockPos pos = site.target(template, step);
			if (!world.isChunkLoaded(pos) || !(world.getBlockEntity(pos) instanceof Inventory inventory)) {
				return false;
			}
			if (!full(inventory)) {
				return false;
			}
			any = true;
		}
		return any;
	}

	/** What a farm's chests and barrels hold: items stored, room in total, containers, and the items, most first. */
	public record Contents(int stored, int capacity, int containers, List<ItemStack> items) {
	}

	/** The farm's storage, or null while any part of it is unloaded. */
	public static Contents contents(ServerWorld world, FarmTemplate template, FarmSite site) {
		int stored = 0;
		int capacity = 0;
		int containers = 0;
		Map<Item, Integer> items = new LinkedHashMap<>();
		for (FarmStep step : template.steps()) {
			if (step.isEntity() || step.state() == null || !storage(step.state().getBlock())) {
				continue;
			}
			BlockPos pos = site.target(template, step);
			if (!world.isChunkLoaded(pos)) {
				return null;
			}
			if (!(world.getBlockEntity(pos) instanceof Inventory inventory)) {
				continue;
			}
			containers++;
			for (int slot = 0; slot < inventory.size(); slot++) {
				ItemStack stack = inventory.getStack(slot);
				capacity += stack.isEmpty() ? inventory.getMaxCountPerStack() : Math.min(stack.getMaxCount(), inventory.getMaxCount(stack));
				if (!stack.isEmpty()) {
					stored += stack.getCount();
					items.merge(stack.getItem(), stack.getCount(), Integer::sum);
				}
			}
		}
		List<ItemStack> sorted = new ArrayList<>();
		items.entrySet().stream()
				.sorted(Map.Entry.<Item, Integer>comparingByValue().reversed())
				.forEach(entry -> sorted.add(new ItemStack(entry.getKey(), entry.getValue())));
		return new Contents(stored, capacity, containers, sorted);
	}

	private static boolean storage(Block block) {
		return block instanceof ChestBlock || block instanceof BarrelBlock;
	}

	private static boolean full(Inventory inventory) {
		for (int slot = 0; slot < inventory.size(); slot++) {
			ItemStack stack = inventory.getStack(slot);
			if (stack.isEmpty() || stack.getCount() < Math.min(stack.getMaxCount(), inventory.getMaxCount(stack))) {
				return false;
			}
		}
		return true;
	}
}
