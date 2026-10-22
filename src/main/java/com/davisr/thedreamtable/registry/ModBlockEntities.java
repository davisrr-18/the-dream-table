package com.davisr.thedreamtable.registry;

import com.davisr.thedreamtable.TheDreamTableMod;
import com.davisr.thedreamtable.block.entity.DreamTableBlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModBlockEntities {
	public static final BlockEntityType<DreamTableBlockEntity> DREAM_TABLE = Registry.register(
			Registries.BLOCK_ENTITY_TYPE,
			Identifier.of(TheDreamTableMod.MOD_ID, "dream_table"),
			BlockEntityType.Builder.create(DreamTableBlockEntity::new, ModBlocks.DREAM_TABLE).build(null)
	);

	private ModBlockEntities() {
	}

	public static void register() {
		TheDreamTableMod.LOGGER.debug("Registered block entities");
	}
}
