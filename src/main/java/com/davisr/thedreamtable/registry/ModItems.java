package com.davisr.thedreamtable.registry;

import com.davisr.thedreamtable.TheDreamTableMod;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModItems {
	public static final Item DREAM_TABLE = Registry.register(
			Registries.ITEM,
			Identifier.of(TheDreamTableMod.MOD_ID, "dream_table"),
			new BlockItem(ModBlocks.DREAM_TABLE, new Item.Settings())
	);

	private ModItems() {
	}

	public static void register() {
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.REDSTONE).register(entries -> entries.add(DREAM_TABLE));
	}
}
