package com.davisr.thedreamtable.block.entity;

import com.davisr.thedreamtable.farm.FarmTemplate;
import com.davisr.thedreamtable.farm.Handover;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Everything a session was given: the counts in the {@link MaterialLedger} and the old material slots, whose stacks
 * are moved into the counts as soon as they are put in. It hands unused items back when the project ends.
 */
final class MaterialDesk {
	private final SimpleInventory slots = new SimpleInventory(FarmTemplate.MAX_SLOTS);
	private final MaterialLedger ledger = new MaterialLedger();
	private final Supplier<FarmTemplate> template;
	private boolean adjusting;

	MaterialDesk(Supplier<FarmTemplate> template, Runnable changed) {
		this.template = template;
		slots.addListener(inventory -> {
			if (!adjusting) {
				absorbSlots();
				changed.run();
			}
		});
	}

	SimpleInventory slots() {
		return slots;
	}

	boolean anyDelivered() {
		return ledger.anyDelivered();
	}

	void reset() {
		ledger.reset();
	}

	// Swaps

	Item resolve(Item original) {
		return ledger.resolve(original);
	}

	BlockState swapState(BlockState state) {
		return ledger.swapState(state);
	}

	Map<Item, Item> swaps() {
		return ledger.swaps();
	}

	void restoreSwaps(Map<Item, Item> saved) {
		ledger.restoreSwaps(saved);
	}

	List<Item> swapOptions(int index) {
		return ledger.swapOptions(template.get(), index);
	}

	boolean untouched(int index) {
		return ledger.untouched(index);
	}

	boolean swap(int index, int option) {
		return ledger.swap(template.get(), index, option);
	}

	// Counts

	/** Takes what the farm still needs from the stack and returns how many. Creative deliveries are counted as free. */
	int accept(ItemStack stack, boolean creative) {
		FarmTemplate farm = template.get();
		return farm == null || stack.isEmpty() ? 0 : ledger.accept(farm, stack, creative);
	}

	boolean complete() {
		FarmTemplate farm = template.get();
		return farm != null && ledger.complete(farm);
	}

	int deposited(int index) {
		FarmTemplate farm = template.get();
		return farm == null ? ledger.deposited(index) : ledger.deposited(farm, index);
	}

	int supplied(int index) {
		FarmTemplate farm = template.get();
		return farm == null ? ledger.supplied(index) : ledger.supplied(farm, index);
	}

	int stored(Item item) {
		FarmTemplate farm = template.get();
		return farm == null ? 0 : ledger.stored(farm, item);
	}

	boolean has(Item item) {
		FarmTemplate farm = template.get();
		return farm != null && ledger.has(farm, item);
	}

	/** Uses one delivered item and returns true when the player paid for it. */
	boolean spend(Item item) {
		FarmTemplate farm = template.get();
		return farm != null && ledger.consume(farm, item);
	}

	/** Gives the unused delivered items and anything left in the slots back to the player, or drops them at the table when there is no player. */
	void returnAll(World world, BlockPos table, PlayerEntity receiver) {
		adjusting = true;
		List<ItemStack> back;
		try {
			back = new ArrayList<>(ledger.drain(template.get()));
			for (int index = 0; index < slots.size(); index++) {
				ItemStack left = slots.getStack(index);
				if (!left.isEmpty()) {
					back.add(left.copy());
				}
			}
			slots.clear();
		} finally {
			adjusting = false;
		}
		hand(back, world, table, receiver);
	}

	/**
	 * Hands back deliveries that no longer fit the farm's design, the same way {@link #returnAll} does, and returns
	 * whether there were any. Without this they would count towards nothing and block new deliveries at their index.
	 */
	boolean returnIncompatible(World world, BlockPos table, PlayerEntity receiver) {
		FarmTemplate farm = template.get();
		if (farm == null) {
			return false;
		}
		List<ItemStack> back = ledger.drainIncompatible(farm);
		hand(back, world, table, receiver);
		return !back.isEmpty();
	}

	private static void hand(List<ItemStack> stacks, World world, BlockPos table, PlayerEntity receiver) {
		boolean dropped = false;
		for (ItemStack stack : stacks) {
			if (receiver != null) {
				dropped |= Handover.give(receiver, stack);
			} else if (world != null) {
				ItemScatterer.spawn(world, table.getX() + 0.5, table.getY() + 0.5, table.getZ() + 0.5, stack);
			}
		}
		if (dropped) {
			Handover.tellDropped(receiver);
		}
	}

	/** True when anything was delivered or still sits in a slot, so there is something to hand back. */
	boolean holdsItems() {
		return ledger.anyDelivered() || !slots.isEmpty();
	}

	private void absorbSlots() {
		FarmTemplate farm = template.get();
		if (farm == null) {
			return;
		}
		adjusting = true;
		try {
			ledger.absorb(farm, slots);
		} finally {
			adjusting = false;
		}
	}

	// Saving

	void write(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		nbt.put("Materials", slots.toNbtList(registries));
		ledger.write(nbt);
	}

	void writeForClient(NbtCompound nbt) {
		ledger.write(nbt);
	}

	void read(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		adjusting = true;
		try {
			slots.clear();
			if (nbt.contains("Materials", NbtElement.LIST_TYPE)) {
				slots.readNbtList(nbt.getList("Materials", NbtElement.COMPOUND_TYPE), registries);
			}
		} finally {
			adjusting = false;
		}
		ledger.read(nbt);
		absorbSlots();
	}
}
