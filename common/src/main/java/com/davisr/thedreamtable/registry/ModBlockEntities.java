package com.davisr.thedreamtable.registry;

import com.davisr.thedreamtable.TheDreamTableMod;
import com.davisr.thedreamtable.block.entity.DreamTableBlockEntity;
import com.davisr.thedreamtable.platform.Platform;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

import java.util.function.Supplier;

public final class ModBlockEntities {
	public static final Supplier<BlockEntityType<DreamTableBlockEntity>> DREAM_TABLE = Platform.content().register(
			RegistryKeys.BLOCK_ENTITY_TYPE,
			new Identifier(TheDreamTableMod.MOD_ID, "dream_table"),
			() -> Platform.content().blockEntityType(DreamTableBlockEntity::new, ModBlocks.DREAM_TABLE.get())
	);

	private ModBlockEntities() {
	}

	public static void register() {
		TheDreamTableMod.LOGGER.debug("Registered block entities");
	}
}
