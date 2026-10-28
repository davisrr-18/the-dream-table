package com.davisr.thedreamtable.platform;

import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemGroup;
import net.minecraft.registry.RegistryKey;

/** Additions to vanilla content the loader has to perform. */
public interface Content {
	/** Adds the item to an existing creative tab when the tab's entries are built. */
	void addToCreativeTab(RegistryKey<ItemGroup> tab, ItemConvertible item);
}
