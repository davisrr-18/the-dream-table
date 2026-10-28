package com.davisr.thedreamtable.farm;

import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
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
		NbtList palette = nbt.getList("Palette", NbtElement.COMPOUND_TYPE);
		List<BlockState> states = new ArrayList<>();
		for (int index = 0; index < palette.size(); index++) {
			states.add(NbtHelper.toBlockState(Registries.BLOCK.getReadOnlyWrapper(), palette.getCompound(index)));
		}
		NbtList itemIds = nbt.getList("Items", NbtElement.STRING_TYPE);
		List<Item> items = new ArrayList<>();
		for (int index = 0; index < itemIds.size(); index++) {
			Identifier itemId = Identifier.tryParse(itemIds.getString(index));
			items.add(itemId == null ? null : Registries.ITEM.get(itemId));
		}
		NbtList entities = nbt.getList("Entities", NbtElement.COMPOUND_TYPE);
		int[] x = nbt.getIntArray("X");
		int[] y = nbt.getIntArray("Y");
		int[] z = nbt.getIntArray("Z");
		int[] state = nbt.getIntArray("State");
		int[] item = nbt.getIntArray("Item");
		Map<Integer, NbtCompound> blockData = new HashMap<>();
		NbtList dataList = nbt.getList("BlockData", NbtElement.COMPOUND_TYPE);
		for (int index = 0; index < dataList.size(); index++) {
			NbtCompound data = dataList.getCompound(index);
			blockData.put(data.getInt("Step"), data.getCompound("Nbt"));
		}
		List<FarmStep> steps = new ArrayList<>();
		int entity = 0;
		for (int index = 0; index < x.length; index++) {
			Item cost = item[index] == NONE ? null : items.get(item[index]);
			if (state[index] == NONE) {
				NbtCompound data = entities.getCompound(entity++);
				EntityType<?> type = EntityType.get(data.getString("Id")).orElse(null);
				if (type == null) {
					continue;
				}
				int flags = data.getInt("Flags");
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
