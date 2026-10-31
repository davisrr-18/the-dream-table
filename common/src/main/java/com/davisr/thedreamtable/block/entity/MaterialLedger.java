package com.davisr.thedreamtable.block.entity;

import com.davisr.thedreamtable.farm.FarmTemplate;
import com.davisr.thedreamtable.farm.MaterialSpec;
import com.davisr.thedreamtable.farm.MaterialSwaps;
import net.minecraft.block.BlockState;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
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
 * Each index also remembers the item it was delivered as, so a refund hands back exactly what the player gave even
 * when the farm is retired, its file is deleted or its design changed since. Saves without that record (written before
 * it existed) fall back to the current template.
 * Items delivered in Creative never left the player's inventory, so they are counted apart in {@code free}:
 * the build uses them first, they never mark a step as paid, and they are never handed back.
 */
public final class MaterialLedger {
	private static final String DELIVERED_KEY = "DeliveredItems";

	/** Upper bound for any saved count (a 64 MB schematic stays far below it), so a corrupt save cannot make a refund build millions of stacks. */
	static final int MAX_COUNT = 16384 * 64;

	private final int[] deposited = new int[FarmTemplate.MAX_SLOTS];
	private final int[] consumed = new int[FarmTemplate.MAX_SLOTS];
	private final int[] free = new int[FarmTemplate.MAX_SLOTS];
	private final Item[] delivered = new Item[FarmTemplate.MAX_SLOTS];
	private final Map<Item, Item> swaps = new LinkedHashMap<>();

	public void reset() {
		Arrays.fill(deposited, 0);
		Arrays.fill(consumed, 0);
		Arrays.fill(free, 0);
		Arrays.fill(delivered, null);
		swaps.clear();
	}

	public Map<Item, Item> swaps() {
		return Map.copyOf(swaps);
	}

	/** Puts back the swaps a finished farm was built with, so repairing it uses the same blocks. */
	public void restoreSwaps(Map<Item, Item> saved) {
		swaps.clear();
		swaps.putAll(saved);
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

	/** What counts for this template: items delivered as something else than its material at this index don't. */
	public int deposited(FarmTemplate template, int index) {
		return usable(template, index) ? deposited[index] : 0;
	}

	public int supplied(FarmTemplate template, int index) {
		return usable(template, index) ? deposited[index] + consumed[index] : 0;
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
			if (deposited(template, index) < template.materials().get(index).count()) {
				return false;
			}
		}
		return true;
	}

	/**
	 * True for a stack with no data components beyond its item's defaults. A refund hands back a default stack of the
	 * delivered item, so anything renamed, enchanted, damaged or holding contents (a shulker box) is never delivered:
	 * it would lose that data.
	 */
	public static boolean plain(ItemStack stack) {
		return stack.getComponentChanges().isEmpty();
	}

	/** How many more of this item the farm still takes, whatever its components. */
	public int remaining(FarmTemplate template, Item item) {
		int needed = 0;
		int have = 0;
		for (int index = 0; index < template.materials().size(); index++) {
			MaterialSpec spec = template.materials().get(index);
			if (item == resolve(spec.item()) && compatible(index, item)) {
				needed += spec.count();
				have += deposited[index] + consumed[index];
			}
		}
		return Math.max(0, needed - have);
	}

	/** Takes up to what the farm still needs of this stack's item and returns how many were taken. Stacks that are not {@link #plain} are refused. */
	public int accept(FarmTemplate template, ItemStack stack, boolean creative) {
		if (stack.isEmpty() || !plain(stack)) {
			return 0;
		}
		int take = Math.min(stack.getCount(), remaining(template, stack.getItem()));
		int accepted = 0;
		for (int index = 0; index < template.materials().size() && accepted < take; index++) {
			MaterialSpec spec = template.materials().get(index);
			int room = spec.count() - deposited[index] - consumed[index];
			if (!stack.isOf(resolve(spec.item())) || !compatible(index, stack.getItem()) || room <= 0) {
				continue;
			}
			int moved = Math.min(room, take - accepted);
			deposited[index] += moved;
			delivered[index] = stack.getItem();
			if (creative) {
				free[index] += moved;
			}
			accepted += moved;
		}
		return accepted;
	}

	/** Moves stacks dropped into the old material slots into the counts, up to what the farm still needs; the rest stays in the slot. */
	public void absorb(FarmTemplate template, Inventory slots) {
		for (int index = 0; index < template.materials().size() && index < slots.size(); index++) {
			ItemStack stack = slots.getStack(index);
			MaterialSpec spec = template.materials().get(index);
			if (stack.isEmpty() || !plain(stack) || !stack.isOf(resolve(spec.item())) || !compatible(index, stack.getItem())) {
				continue;
			}
			int moved = Math.min(stack.getCount(), spec.count() - deposited[index] - consumed[index]);
			if (moved <= 0) {
				continue;
			}
			deposited[index] += moved;
			delivered[index] = stack.getItem();
			stack.decrement(moved);
			if (stack.isEmpty()) {
				slots.setStack(index, ItemStack.EMPTY);
			}
		}
	}

	public int stored(FarmTemplate template, Item original) {
		int count = 0;
		for (int index = 0; index < template.materials().size(); index++) {
			if (template.materials().get(index).item() == original && compatible(index, resolve(original))) {
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

	/**
	 * Empties the counts and returns the delivered items that were not used yet, leaving out Creative ones. The items come
	 * from what was delivered, so the template may be gone or different; only saves without that record use the template,
	 * and without a template either those items are unknown and cannot be returned.
	 */
	public List<ItemStack> drain(FarmTemplate template) {
		List<ItemStack> stacks = new ArrayList<>();
		for (int index = 0; index < deposited.length; index++) {
			int left = deposited[index] - free[index];
			Item item = delivered[index];
			if (item == null && template != null && index < template.materials().size()) {
				item = resolve(template.materials().get(index).item());
			}
			deposited[index] = 0;
			consumed[index] = 0;
			free[index] = 0;
			delivered[index] = null;
			if (left <= 0 || item == null) {
				continue;
			}
			refund(stacks, item, left);
		}
		return stacks;
	}

	/**
	 * Empties the indexes whose delivered items don't fit the template's material there, and returns the unspent ones,
	 * leaving out Creative ones. That happens when the design changed under a delivery. Items already used stay used: they
	 * were placed as blocks of the old design, so they are cleared without a refund. Indexes the template no longer has
	 * are emptied the same way. Deliveries from before the record existed are left alone, since their item is unknown.
	 */
	public List<ItemStack> drainIncompatible(FarmTemplate template) {
		List<ItemStack> stacks = new ArrayList<>();
		for (int index = 0; index < deposited.length; index++) {
			if (delivered[index] == null || usable(template, index)) {
				continue;
			}
			refund(stacks, delivered[index], deposited[index] - free[index]);
			deposited[index] = 0;
			consumed[index] = 0;
			free[index] = 0;
			delivered[index] = null;
		}
		return stacks;
	}

	/** Writes copies of the counts: {@code NbtIntArray} keeps the array it is given, so a kept save would follow later changes. */
	public void write(NbtCompound nbt) {
		nbt.putIntArray("Deposited", deposited.clone());
		nbt.putIntArray("Consumed", consumed.clone());
		nbt.putIntArray("FreeDeposited", free.clone());
		writeDelivered(nbt);
		writeSwaps(nbt, swaps);
	}

	public void read(NbtCompound nbt) {
		copy(nbt.getIntArray("Deposited"), deposited);
		copy(nbt.getIntArray("Consumed"), consumed);
		copy(nbt.getIntArray("FreeDeposited"), free);
		for (int index = 0; index < deposited.length; index++) {
			deposited[index] = Math.max(0, Math.min(deposited[index], MAX_COUNT));
			consumed[index] = Math.max(0, Math.min(consumed[index], MAX_COUNT));
			free[index] = Math.max(0, Math.min(free[index], deposited[index]));
		}
		readDelivered(nbt);
		swaps.clear();
		swaps.putAll(readSwaps(nbt));
	}

	/** Additive key: older saves lack it and older versions ignore it. One item id per index, empty where nothing was delivered. */
	private void writeDelivered(NbtCompound nbt) {
		int last = delivered.length - 1;
		while (last >= 0 && delivered[last] == null) {
			last--;
		}
		NbtList list = new NbtList();
		for (int index = 0; index <= last; index++) {
			list.add(NbtString.of(delivered[index] == null ? "" : Registries.ITEM.getId(delivered[index]).toString()));
		}
		nbt.put(DELIVERED_KEY, list);
	}

	private void readDelivered(NbtCompound nbt) {
		Arrays.fill(delivered, null);
		NbtList list = nbt.getList(DELIVERED_KEY, NbtElement.STRING_TYPE);
		for (int index = 0; index < list.size() && index < delivered.length; index++) {
			Identifier id = Identifier.tryParse(list.getString(index));
			if (id != null && Registries.ITEM.containsId(id)) {
				Item item = Registries.ITEM.get(id);
				delivered[index] = item == Items.AIR ? null : item;
			}
		}
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

	private static void refund(List<ItemStack> stacks, Item item, int left) {
		while (left > 0) {
			int amount = Math.min(left, Math.max(1, item.getMaxCount()));
			stacks.add(new ItemStack(item, amount));
			left -= amount;
		}
	}

	/** True when the index exists in the template and what it holds, if anything, was delivered for its material. */
	private boolean usable(FarmTemplate template, int index) {
		if (!valid(index)) {
			return false;
		}
		if (index >= template.materials().size()) {
			return deposited[index] == 0 && consumed[index] == 0;
		}
		return compatible(index, resolve(template.materials().get(index).item()));
	}

	/** False when this index holds items delivered as something else, which happens when the design changed under it. */
	private boolean compatible(int index, Item item) {
		return delivered[index] == null || delivered[index] == item || deposited[index] == 0 && consumed[index] == 0;
	}

	private int find(FarmTemplate template, Item original) {
		for (int index = 0; index < template.materials().size(); index++) {
			if (template.materials().get(index).item() == original && deposited[index] > 0 && compatible(index, resolve(original))) {
				return index;
			}
		}
		return -1;
	}

	private boolean valid(int index) {
		return index >= 0 && index < deposited.length;
	}
}
