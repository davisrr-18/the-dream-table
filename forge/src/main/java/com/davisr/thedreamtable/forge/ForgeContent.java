package com.davisr.thedreamtable.forge;

import com.davisr.thedreamtable.platform.Content;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemGroup;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Forge's deferred registers and creative tab event, on the mod bus. Registries are frozen once mods are
 * constructed, so entries are created during Forge's registration phase.
 */
final class ForgeContent implements Content {
	private final IEventBus modBus;
	private final Map<String, DeferredRegister<?>> registers = new HashMap<>();

	ForgeContent(IEventBus modBus) {
		this.modBus = modBus;
	}

	@Override
	@SuppressWarnings("unchecked")
	public <R, T extends R> Supplier<T> register(RegistryKey<? extends Registry<R>> registry, Identifier id, Supplier<T> factory) {
		String key = registry.getValue() + "|" + id.getNamespace();
		DeferredRegister<R> deferred = (DeferredRegister<R>) registers.computeIfAbsent(key, ignored -> {
			DeferredRegister<R> created = DeferredRegister.create(registry, id.getNamespace());
			created.register(modBus);
			return created;
		});
		return deferred.register(id.getPath(), factory);
	}

	@Override
	public void addToCreativeTab(RegistryKey<ItemGroup> tab, Supplier<? extends ItemConvertible> item) {
		ForgePlatform.listen(modBus, BuildCreativeModeTabContentsEvent.class, event -> {
			if (event.getTabKey().equals(tab)) {
				event.accept(item);
			}
		});
	}

	@Override
	public <T extends BlockEntity> BlockEntityType<T> blockEntityType(BlockEntityFactory<T> factory, Block... blocks) {
		return BlockEntityType.Builder.create(factory::create, blocks).build(null);
	}
}
