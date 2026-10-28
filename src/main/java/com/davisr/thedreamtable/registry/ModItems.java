package com.davisr.thedreamtable.registry;

import com.davisr.thedreamtable.TheDreamTableMod;
import com.davisr.thedreamtable.platform.Platform;
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
		Platform.content().addToCreativeTab(ItemGroups.REDSTONE, DREAM_TABLE);
	}
}
