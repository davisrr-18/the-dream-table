package com.davisr.thedreamtable.registry;

import com.davisr.thedreamtable.TheDreamTableMod;
import com.davisr.thedreamtable.block.DreamTableBlock;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.enums.NoteBlockInstrument;
import com.davisr.thedreamtable.platform.Platform;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

import java.util.function.Supplier;

public final class ModBlocks {
	public static final Supplier<Block> DREAM_TABLE = register(
			"dream_table",
			// 1.21.2+ block settings must carry their registry key, or creating the block throws.
			() -> new DreamTableBlock(AbstractBlock.Settings.create()
					.registryKey(key("dream_table"))
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

	static RegistryKey<Block> key(String path) {
		return RegistryKey.of(RegistryKeys.BLOCK, Identifier.of(TheDreamTableMod.MOD_ID, path));
	}

	private static Supplier<Block> register(String path, Supplier<Block> block) {
		return Platform.content().register(RegistryKeys.BLOCK, key(path).getValue(), block);
	}
}
