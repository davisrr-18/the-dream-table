package com.davisr.redstonedreams.farm;

import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;

public record FarmStep(int x, int y, int z, BlockState state, Item item, EntityType<?> entity, boolean noAi, boolean minecart, NbtCompound blockNbt) {
	public static FarmStep block(int x, int y, int z, BlockState state, Item item) {
		return new FarmStep(x, y, z, state, item, null, false, false, null);
	}

	public static FarmStep block(int x, int y, int z, BlockState state, Item item, NbtCompound blockNbt) {
		return new FarmStep(x, y, z, state, item, null, false, false, blockNbt);
	}

	public static FarmStep entity(int x, int y, int z, EntityType<?> entity, boolean noAi) {
		return new FarmStep(x, y, z, null, null, entity, noAi, false, null);
	}

	public static FarmStep minecart(int x, int y, int z, EntityType<?> passenger) {
		return new FarmStep(x, y, z, null, null, passenger, true, true, null);
	}

	public boolean isEntity() {
		return entity != null;
	}

	public FarmStep withItem(Item item) {
		return new FarmStep(x, y, z, state, item, entity, noAi, minecart, blockNbt);
	}
}
