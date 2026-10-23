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
 * Items delivered in Creative never left the player's inventory, so they are counted apart in {@code free}:
 * the build uses them first, they never mark a step as paid, and they are never handed back.
 */
public final class MaterialLedger {
	private final int[] deposited = new int[FarmTemplate.MAX_SLOTS];
	private final int[] consumed = new int[FarmTemplate.MAX_SLOTS];
	private final int[] free = new int[FarmTemplate.MAX_SLOTS];
	private final Map<Item, Item> swaps = new LinkedHashMap<>();

	public void reset() {
		Arrays.fill(deposited, 0);
		Arrays.fill(consumed, 0);
		Arrays.fill(free, 0);
		swaps.clear();
	}

	public Map<Item, Item> swaps() {
		return Map.copyOf(swaps);
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
	public int accept(FarmTemplate template, ItemStack stack, boolean creative) {
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
			if (creative) {
				free[index] += moved;
			}
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

	/** Uses one delivered item, Creative ones first. Returns true only when the item used was one the player paid for. */
	public boolean consume(FarmTemplate template, Item original) {
		int index = find(template, original);
		if (index < 0) {
			return false;
		}
		deposited[index]--;
		consumed[index]++;
		if (free[index] > 0) {
			free[index]--;
			return false;
		}
		return true;
	}

	/** Empties the counts and returns the delivered items that were not used yet, leaving out Creative ones. */
	public List<ItemStack> drain(FarmTemplate template) {
		List<ItemStack> stacks = new ArrayList<>();
		for (int index = 0; index < deposited.length; index++) {
			int left = deposited[index] - free[index];
			deposited[index] = 0;
			consumed[index] = 0;
			free[index] = 0;
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
		nbt.putIntArray("FreeDeposited", free);
		writeSwaps(nbt, swaps);
	}

	public void read(NbtCompound nbt) {
		copy(nbt.getIntArray("Deposited"), deposited);
		copy(nbt.getIntArray("Consumed"), consumed);
		copy(nbt.getIntArray("FreeDeposited"), free);
		swaps.clear();
		swaps.putAll(readSwaps(nbt));
	}

	public static void writeSwaps(NbtCompound nbt, Map<Item, Item> swaps) {
		NbtList list = new NbtList();
		for (Map.Entry<Item, Item> swap : swaps.entrySet()) {
			NbtCompound entry = new NbtCompound();
			entry.putString("From", Registries.ITEM.getId(swap.getKey()).toString());
			entry.putString("To", Registries.ITEM.getId(swap.getValue()).toString());
			list.add(entry);
		}
		nbt.put("Swaps", list);
	}

	public static Map<Item, Item> readSwaps(NbtCompound nbt) {
		Map<Item, Item> swaps = new LinkedHashMap<>();
		NbtList list = nbt.getList("Swaps", NbtElement.COMPOUND_TYPE);
		for (int index = 0; index < list.size(); index++) {
			Identifier from = Identifier.tryParse(list.getCompound(index).getString("From"));
			Identifier to = Identifier.tryParse(list.getCompound(index).getString("To"));
			if (from != null && to != null && Registries.ITEM.containsId(from) && Registries.ITEM.containsId(to)) {
				swaps.put(Registries.ITEM.get(from), Registries.ITEM.get(to));
			}
		}
		return swaps;
	}

	private static void copy(int[] from, int[] into) {
		Arrays.fill(into, 0);
		System.arraycopy(from, 0, into, 0, Math.min(from.length, into.length));
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
