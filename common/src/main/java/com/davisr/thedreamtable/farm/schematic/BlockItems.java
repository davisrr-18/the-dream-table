package com.davisr.thedreamtable.farm.schematic;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.state.property.Properties;

import java.util.Map;

/** Which item the player hands over for a block or an entity. Null means the step is free. */
final class BlockItems {
	private static final Map<Block, Item> PLACED_BY = Map.ofEntries(
			Map.entry(Blocks.BAMBOO_SAPLING, Items.BAMBOO),
			Map.entry(Blocks.WATER, Items.WATER_BUCKET),
			Map.entry(Blocks.LAVA, Items.LAVA_BUCKET),
			Map.entry(Blocks.FARMLAND, Items.DIRT),
			Map.entry(Blocks.DIRT_PATH, Items.DIRT),
			Map.entry(Blocks.REDSTONE_WIRE, Items.REDSTONE),
			Map.entry(Blocks.WHEAT, Items.WHEAT_SEEDS),
			Map.entry(Blocks.CARROTS, Items.CARROT),
			Map.entry(Blocks.POTATOES, Items.POTATO),
			Map.entry(Blocks.BEETROOTS, Items.BEETROOT_SEEDS),
			Map.entry(Blocks.REDSTONE_WALL_TORCH, Items.REDSTONE_TORCH),
			Map.entry(Blocks.WALL_TORCH, Items.TORCH));

	private static final Map<EntityType<?>, Item> CARTS = Map.of(
			EntityType.HOPPER_MINECART, Items.HOPPER_MINECART,
			EntityType.CHEST_MINECART, Items.CHEST_MINECART,
			EntityType.FURNACE_MINECART, Items.FURNACE_MINECART,
			EntityType.TNT_MINECART, Items.TNT_MINECART,
			EntityType.MINECART, Items.MINECART);

	private BlockItems() {
	}

	/** The upper half of a door and the head of a bed come with the item placed for the other half. */
	static Item itemFor(BlockState state) {
		Block block = state.getBlock();
		if (block instanceof DoorBlock && state.contains(DoorBlock.HALF) && state.get(DoorBlock.HALF) == DoubleBlockHalf.UPPER) {
			return null;
		}
		if (state.contains(Properties.BED_PART) && state.get(Properties.BED_PART) == BedPart.HEAD) {
			return null;
		}
		Item mapped = PLACED_BY.get(block);
		if (mapped != null) {
			return mapped;
		}
		Item item = block.asItem();
		return item == Items.AIR ? null : item;
	}

	static Item cost(EntityType<?> type) {
		SpawnEggItem egg = SpawnEggItem.forEntity(type);
		return egg != null ? egg : CARTS.get(type);
	}
}
