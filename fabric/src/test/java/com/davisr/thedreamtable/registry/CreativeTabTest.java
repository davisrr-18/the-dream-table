package com.davisr.thedreamtable.registry;

import net.minecraft.item.ItemGroups;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CreativeTabTest {
	@Test
	void tabKeyNamesVanillaRedstoneBlocks() {
		assertEquals(ItemGroups.REDSTONE, ModItems.CreativeTab.KEY);
	}
}
