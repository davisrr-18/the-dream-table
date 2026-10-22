package com.davisr.thedreamtable.block.entity;

import com.davisr.thedreamtable.farm.FarmTemplate;
import com.davisr.thedreamtable.farm.MaterialSpec;
import com.davisr.thedreamtable.farm.MaterialSwaps;
import net.minecraft.block.BlockState;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Counts per template material: what is delivered and waiting, and what the build already used.
 * Indexes follow {@link FarmTemplate#materials()}; counts are always kept under the original item,
 * while {@link #resolve(Item)} gives the item the player actually delivers after a swap.
 */
public final class MaterialLedger {
	private final int[] deposited = new int[FarmTemplate.MAX_SLOTS];
	private final int[] consumed = new int[FarmTemplate.MAX_SLOTS];
	private final Map<Item, Item> swaps = new LinkedHashMap<>();

	public void reset() {
		Arrays.fill(deposited, 0);
		Arrays.fill(consumed, 0);
		swaps.clear();
	}

	public boolean anyDelivered() {
		for (int index = 0; index < deposited.length; index++) {
			if (deposited[index] > 0 || consumed[index] > 0) {
				return true;
			}
		}
		return false;
	}

	public int deposited(int index) {
		return valid(index) ? deposited[index] : 0;
	}

	public int supplied(int index) {
		return valid(index) ? deposited[index] + consumed[index] : 0;
	}

	public boolean untouched(int index) {
		return valid(index) && deposited[index] == 0 && consumed[index] == 0;
	}

	public Item resolve(Item original) {
		return swaps.getOrDefault(original, original);
	}

	public BlockState swapState(BlockState state) {
		return MaterialSwaps.apply(state, swaps);
	}

	public List<Item> swapOptions(FarmTemplate template, int index) {
		if (template == null || index < 0 || index >= template.materials().size()) {
			return List.of();
		}
		return MaterialSwaps.options(template, template.materials().get(index).item());
	}

	public boolean swap(FarmTemplate template, int index, int option) {
		List<Item> options = swapOptions(template, index);
		if (option < 0 || option >= options.size()) {
			return false;
		}
		Item original = template.materials().get(index).item();
		Item chosen = options.get(option);
		if (chosen == original) {
			swaps.remove(original);
		} else {
			swaps.put(original, chosen);
		}
		return true;
	}

	public boolean complete(FarmTemplate template) {
		for (int index = 0; index < template.materials().size(); index++) {
			if (deposited[index] < template.materials().get(index).count()) {
				return false;
			}
		}
		return true;
	}

	/** Takes up to what the farm still needs of this stack's item and returns how many were taken. */
	public int accept(FarmTemplate template, ItemStack stack) {
		int needed = 0;
		int have = 0;
		for (int index = 0; index < template.materials().size(); index++) {
			MaterialSpec spec = template.materials().get(index);
			if (stack.isOf(resolve(spec.item()))) {
				needed += spec.count();
				have += deposited[index] + consumed[index];
			}
		}
		int take = Math.min(stack.getCount(), needed - have);
		int accepted = 0;
		for (int index = 0; index < template.materials().size() && accepted < take; index++) {
			MaterialSpec spec = template.materials().get(index);
			int room = spec.count() - deposited[index];
			if (!stack.isOf(resolve(spec.item())) || room <= 0) {
				continue;
			}
			int moved = Math.min(room, take - accepted);
			deposited[index] += moved;
			accepted += moved;
		}
		return accepted;
	}

	/** Moves stacks dropped into the old material slots into the counts. */
	public void absorb(FarmTemplate template, Inventory slots) {
		for (int index = 0; index < template.materials().size() && index < slots.size(); index++) {
			ItemStack stack = slots.getStack(index);
			if (stack.isEmpty() || !stack.isOf(resolve(template.materials().get(index).item()))) {
				continue;
			}
			deposited[index] += stack.getCount();
			slots.setStack(index, ItemStack.EMPTY);
		}
	}

	public int stored(FarmTemplate template, Item original) {
		int count = 0;
		for (int index = 0; index < template.materials().size(); index++) {
			if (template.materials().get(index).item() == original) {
				count += deposited[index] + consumed[index];
			}
		}
		return count;
	}

	public boolean has(FarmTemplate template, Item original) {
		return find(template, original) >= 0;
	}

	public boolean consume(FarmTemplate template, Item original) {
		int index = find(template, original);
		if (index < 0) {
			return false;
		}
		deposited[index]--;
		consumed[index]++;
		return true;
	}

	/** Empties the counts and returns the delivered items that were not used yet. */
	public List<ItemStack> drain(FarmTemplate template) {
		List<ItemStack> stacks = new ArrayList<>();
		for (int index = 0; index < deposited.length; index++) {
			int left = deposited[index];
			deposited[index] = 0;
			consumed[index] = 0;
			if (left <= 0 || template == null || index >= template.materials().size()) {
				continue;
			}
			Item item = resolve(template.materials().get(index).item());
			while (left > 0) {
				int amount = Math.min(left, Math.max(1, item.getMaxCount()));
				stacks.add(new ItemStack(item, amount));
				left -= amount;
			}
		}
		return stacks;
	}

	public void write(NbtCompound nbt) {
		nbt.putIntArray("Deposited", deposited);
		nbt.putIntArray("Consumed", consumed);
		NbtList list = new NbtList();
		for (Map.Entry<Item, Item> swap : swaps.entrySet()) {
			NbtCompound entry = new NbtCompound();
			entry.putString("From", Registries.ITEM.getId(swap.getKey()).toString());
			entry.putString("To", Registries.ITEM.getId(swap.getValue()).toString());
			list.add(entry);
		}
		nbt.put("Swaps", list);
	}

	public void read(NbtCompound nbt) {
		Arrays.fill(deposited, 0);
		Arrays.fill(consumed, 0);
		int[] stored = nbt.getIntArray("Deposited");
		int[] used = nbt.getIntArray("Consumed");
		System.arraycopy(stored, 0, deposited, 0, Math.min(stored.length, deposited.length));
		System.arraycopy(used, 0, consumed, 0, Math.min(used.length, consumed.length));
		swaps.clear();
		NbtList list = nbt.getList("Swaps", NbtElement.COMPOUND_TYPE);
		for (int index = 0; index < list.size(); index++) {
			Identifier from = Identifier.tryParse(list.getCompound(index).getString("From"));
			Identifier to = Identifier.tryParse(list.getCompound(index).getString("To"));
			if (from != null && to != null && Registries.ITEM.containsId(from) && Registries.ITEM.containsId(to)) {
				swaps.put(Registries.ITEM.get(from), Registries.ITEM.get(to));
			}
		}
	}

	private int find(FarmTemplate template, Item original) {
		for (int index = 0; index < template.materials().size(); index++) {
			if (template.materials().get(index).item() == original && deposited[index] > 0) {
				return index;
			}
		}
		return -1;
	}

	private boolean valid(int index) {
		return index >= 0 && index < deposited.length;
	}
}
