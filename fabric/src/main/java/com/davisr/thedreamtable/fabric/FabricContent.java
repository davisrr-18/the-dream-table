package com.davisr.thedreamtable.fabric;

import com.davisr.thedreamtable.platform.Content;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;

import java.util.function.Supplier;

/** Fabric API's creative tab event. */
final class FabricContent implements Content {
	@Override
	@SuppressWarnings("unchecked")
	public <R, T extends R> Supplier<T> register(RegistryKey<? extends Registry<R>> registry, Identifier id, Supplier<T> factory) {
		// Fabric registers at once, so the entry exists as soon as the mod's setup runs.
		Registry<R> target = (Registry<R>) Registries.REGISTRIES.get(registry.getValue());
		T entry = Registry.register(target, id, factory.get());
		return () -> entry;
	}

	@Override
	public void addToCreativeTab(RegistryKey<ItemGroup> tab, Supplier<? extends ItemConvertible> item) {
		ItemGroupEvents.modifyEntriesEvent(tab).register(entries -> entries.add(item.get()));
	}

	@Override
	public <T extends BlockEntity> BlockEntityType<T> blockEntityType(BlockEntityFactory<T> factory, Block... blocks) {
		return BlockEntityType.Builder.create(factory::create, blocks).build(null);
	}
}
