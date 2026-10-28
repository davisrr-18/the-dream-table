package com.davisr.thedreamtable.platform;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemGroup;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.function.Supplier;

/** Additions to vanilla content the loader has to perform. */
public interface Content {
	/**
	 * Registers the entry the factory creates under the id. Some loaders create it later, during their registration
	 * phase, so read it through the returned supplier only after mod setup.
	 */
	<R, T extends R> Supplier<T> register(RegistryKey<? extends Registry<R>> registry, Identifier id, Supplier<T> factory);

	/** Adds the item to an existing creative tab when the tab's entries are built. */
	void addToCreativeTab(RegistryKey<ItemGroup> tab, Supplier<? extends ItemConvertible> item);

	/** A block entity type that is not registered yet; the caller registers it under its own id. */
	<T extends BlockEntity> BlockEntityType<T> blockEntityType(BlockEntityFactory<T> factory, Block... blocks);

	@FunctionalInterface
	interface BlockEntityFactory<T extends BlockEntity> {
		T create(BlockPos pos, BlockState state);
	}
}
