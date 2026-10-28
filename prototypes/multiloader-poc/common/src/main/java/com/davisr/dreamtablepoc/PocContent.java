package com.davisr.dreamtablepoc;

import dev.architectury.registry.menu.MenuRegistry;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.sound.BlockSoundGroup;

public final class PocContent {
	private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(DreamTablePoc.MOD_ID, RegistryKeys.BLOCK);
	private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(DreamTablePoc.MOD_ID, RegistryKeys.ITEM);

	private static final DeferredRegister<ScreenHandlerType<?>> MENUS = DeferredRegister.create(DreamTablePoc.MOD_ID, RegistryKeys.SCREEN_HANDLER);

	// Created eagerly so client init can hand the type to MenuRegistry.registerScreenFactory before registries open on NeoForge.
	public static final ScreenHandlerType<CounterMenu> COUNTER_MENU_TYPE = MenuRegistry.ofExtended((syncId, inventory, buf) ->
			new CounterMenu(syncId, inventory, buf.readBlockPos(), buf.readVarInt()));

	public static final RegistrySupplier<Block> COUNTER_BLOCK = BLOCKS.register("counter_block",
			() -> new CounterBlock(AbstractBlock.Settings.create().strength(1.5f).sounds(BlockSoundGroup.AMETHYST_BLOCK)));
	public static final RegistrySupplier<Item> COUNTER_BLOCK_ITEM = ITEMS.register("counter_block",
			() -> new BlockItem(COUNTER_BLOCK.get(), new Item.Settings()));

	public static final RegistrySupplier<ScreenHandlerType<CounterMenu>> COUNTER_MENU = MENUS.register("counter_menu", () -> COUNTER_MENU_TYPE);

	private PocContent() {
	}

	static void register() {
		BLOCKS.register();
		ITEMS.register();
		MENUS.register();
	}
}
