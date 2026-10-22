package com.davisr.thedreamtable.registry;

import com.davisr.thedreamtable.TheDreamTableMod;
import com.davisr.thedreamtable.block.DreamTableBlock;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.enums.NoteBlockInstrument;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

public final class ModBlocks {
	public static final Block DREAM_TABLE = register(
			"dream_table",
			new DreamTableBlock(AbstractBlock.Settings.create()
					.mapColor(MapColor.OAK_TAN)
					.instrument(NoteBlockInstrument.BASS)
					.strength(2.0F)
					.sounds(BlockSoundGroup.WOOD)
					.burnable()
					.nonOpaque()
					.solidBlock((state, world, pos) -> false)
					.suffocates((state, world, pos) -> false)
					.blockVision((state, world, pos) -> false))
	);

	private ModBlocks() {
	}

	public static void register() {
		TheDreamTableMod.LOGGER.debug("Registered blocks");
	}

	private static Block register(String path, Block block) {
		return Registry.register(Registries.BLOCK, Identifier.of(TheDreamTableMod.MOD_ID, path), block);
	}
}
