package com.davisr.redstonedreams.registry;

import com.davisr.redstonedreams.RedstoneDreamsMod;
import com.davisr.redstonedreams.block.entity.DreamTableBlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModBlockEntities {
	public static final BlockEntityType<DreamTableBlockEntity> DREAM_TABLE = Registry.register(
			Registries.BLOCK_ENTITY_TYPE,
			Identifier.of(RedstoneDreamsMod.MOD_ID, "dream_table"),
			BlockEntityType.Builder.create(DreamTableBlockEntity::new, ModBlocks.DREAM_TABLE).build(null)
	);

	private ModBlockEntities() {
	}

	public static void register() {
		RedstoneDreamsMod.LOGGER.debug("Registered block entities");
	}
}
