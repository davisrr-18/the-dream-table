package com.davisr.thedreamtable.registry;

import com.davisr.thedreamtable.TheDreamTableMod;
import com.davisr.thedreamtable.platform.Platform;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

import java.util.function.Supplier;

public final class ModItems {
	public static final Supplier<Item> DREAM_TABLE = Platform.content().register(
			RegistryKeys.ITEM,
			Identifier.of(TheDreamTableMod.MOD_ID, "dream_table"),
			() -> new BlockItem(ModBlocks.DREAM_TABLE.get(), new Item.Settings())
	);

	private ModItems() {
	}

	public static void register() {
		Platform.content().addToCreativeTab(CreativeTab.KEY, DREAM_TABLE);
	}

	/** Vanilla's Redstone Blocks tab, named by id because its constant is private outside a loader. */
	static final class CreativeTab {
		static final RegistryKey<ItemGroup> KEY = RegistryKey.of(RegistryKeys.ITEM_GROUP, Identifier.ofVanilla("redstone_blocks"));

		private CreativeTab() {
		}
	}
}
