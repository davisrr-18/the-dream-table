package com.davisr.thedreamtable.fabric;

import com.davisr.thedreamtable.platform.Content;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemGroup;
import net.minecraft.registry.RegistryKey;

/** Fabric API's creative tab event. */
final class FabricContent implements Content {
	@Override
	public void addToCreativeTab(RegistryKey<ItemGroup> tab, ItemConvertible item) {
		ItemGroupEvents.modifyEntriesEvent(tab).register(entries -> entries.add(item));
	}
}
