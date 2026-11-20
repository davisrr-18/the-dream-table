package com.davisr.thedreamtable.farm;

import com.davisr.thedreamtable.DataFixes;
import com.davisr.thedreamtable.Nbts;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A farm as compact NBT: for sending a server's imported schematic to clients that don't have the file, and for keeping
 * a finished farm's design in the world. Block states and items are listed once and steps point into those lists.
 * Block entity data, like a hopper's filter items, is kept only when asked, for the world copy; clients only draw farms.
 */
public final class TemplateCodec {
	private static final int NONE = -1;
	private static final int NO_AI = 1;
	private static final int MINECART = 2;

	private TemplateCodec() {
	}

	public static NbtCompound encode(FarmTemplate template) {
		return encode(template, false);
	}

	public static NbtCompound encode(FarmTemplate template, boolean withBlockData) {
		Map<BlockState, Integer> states = new HashMap<>();
		Map<Item, Integer> items = new HashMap<>();
		NbtList palette = new NbtList();
		NbtList itemIds = new NbtList();
		NbtList entities = new NbtList();
		NbtList blockData = new NbtList();
		List<FarmStep> steps = template.steps();
		int[] x = new int[steps.size()];
		int[] y = new int[steps.size()];
		int[] z = new int[steps.size()];
		int[] state = new int[steps.size()];
		int[] item = new int[steps.size()];
		for (int index = 0; index < steps.size(); index++) {
			FarmStep step = steps.get(index);
			x[index] = step.x();
			y[index] = step.y();
			z[index] = step.z();
			item[index] = step.item() == null ? NONE : items.computeIfAbsent(step.item(), added -> {
				itemIds.add(NbtString.of(Registries.ITEM.getId(added).toString()));
				return itemIds.size() - 1;
			});
			if (step.isEntity()) {
				state[index] = NONE;
				NbtCompound entity = new NbtCompound();
				entity.putString("Id", EntityType.getId(step.entity()).toString());
				entity.putInt("Flags", (step.noAi() ? NO_AI : 0) | (step.minecart() ? MINECART : 0));
				entities.add(entity);
			} else {
				state[index] = states.computeIfAbsent(step.state(), added -> {
					palette.add(NbtHelper.fromBlockState(added));
					return palette.size() - 1;
				});
				if (withBlockData && step.blockNbt() != null) {
					NbtCompound data = new NbtCompound();
					data.putInt("Step", index);
					data.put("Nbt", step.blockNbt().copy());
					blockData.add(data);
				}
			}
		}
		NbtCompound nbt = new NbtCompound();
		// The game's data version at the time: ids are kept as written, and decode updates older ones by it. Designs
		// saved before 1.21.11 have none and read as written by 1.21.1.
		nbt.putInt("DataVersion", DataFixes.current());
		nbt.put("Palette", palette);
		nbt.put("Items", itemIds);
		nbt.put("Entities", entities);
		nbt.putIntArray("X", x);
		nbt.putIntArray("Y", y);
		nbt.putIntArray("Z", z);
		nbt.putIntArray("State", state);
		nbt.putIntArray("Item", item);
		if (!blockData.isEmpty()) {
			nbt.put("BlockData", blockData);
		}
		return nbt;
	}

	public static FarmTemplate decode(String id, NbtCompound nbt) {
		// The list readers throw on a wrong-typed entry: a malformed saved layout must fail whole (and be kept as it was
		// by the caller), never decode with placeholder entries.
		int from = nbt.getInt("DataVersion", DataFixes.ASSUMED);
		List<BlockState> states = new ArrayList<>();
		for (NbtCompound entry : Nbts.compounds(nbt, "Palette")) {
			states.add(NbtHelper.toBlockState(Registries.BLOCK, DataFixes.blockState(entry, from)));
		}
		List<Item> items = new ArrayList<>();
		for (String raw : Nbts.strings(nbt, "Items")) {
			Identifier itemId = Identifier.tryParse(DataFixes.itemName(raw, from));
			// An item this game no longer has reads as air, as the registry's default did before 1.21.2 (get is nullable now).
			items.add(itemId == null ? null : Registries.ITEM.getOptionalValue(itemId).orElse(Items.AIR));
		}
		List<NbtCompound> entities = Nbts.compounds(nbt, "Entities");
		int[] x = Nbts.ints(nbt, "X");
		int[] y = Nbts.ints(nbt, "Y");
		int[] z = Nbts.ints(nbt, "Z");
		int[] state = Nbts.ints(nbt, "State");
		int[] item = Nbts.ints(nbt, "Item");
		Map<Integer, NbtCompound> blockData = new HashMap<>();
		for (NbtCompound data : Nbts.compounds(nbt, "BlockData")) {
			blockData.put(data.getInt("Step", 0), data.getCompoundOrEmpty("Nbt"));
		}
		List<FarmStep> steps = new ArrayList<>();
		int entity = 0;
		for (int index = 0; index < x.length; index++) {
			Item cost = item[index] == NONE ? null : items.get(item[index]);
			if (state[index] == NONE) {
				// Past the end reads as an empty entry (the entity is skipped), as the old list getter did.
				NbtCompound data = entity < entities.size() ? entities.get(entity) : new NbtCompound();
				entity++;
				EntityType<?> type = EntityType.get(DataFixes.entityName(data.getString("Id", ""), from)).orElse(null);
				if (type == null) {
					continue;
				}
				int flags = data.getInt("Flags", 0);
				FarmStep step = (flags & MINECART) != 0
						? FarmStep.minecart(x[index], y[index], z[index], type)
						: FarmStep.entity(x[index], y[index], z[index], type, (flags & NO_AI) != 0);
				steps.add(step.withItem(cost));
			} else {
				NbtCompound data = blockData.get(index);
				steps.add(data == null
						? FarmStep.block(x[index], y[index], z[index], states.get(state[index]), cost)
						: FarmStep.block(x[index], y[index], z[index], states.get(state[index]), cost, data));
			}
		}
		return FarmTemplate.of(id, steps);
	}
}
