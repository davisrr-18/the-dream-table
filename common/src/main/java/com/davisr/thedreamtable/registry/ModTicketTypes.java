package com.davisr.thedreamtable.registry;

import com.davisr.thedreamtable.TheDreamTableMod;
import com.davisr.thedreamtable.platform.Platform;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.util.Identifier;

import java.util.function.Supplier;

/**
 * Chunk ticket types. From 1.21.2 they are a registry, so they are registered with the mod's content instead of created
 * on the fly. They load and simulate the chunk (as the old ticket types did), never serialize, and expire on their own.
 */
public final class ModTicketTypes {
	private static final int LOADING_AND_SIMULATION = ChunkTicketType.FOR_LOADING | ChunkTicketType.FOR_SIMULATION;

	/** A running build's table and the spot being built, renewed while the build runs. */
	public static final Supplier<ChunkTicketType> BUILD = register("build", 100);
	/** A picked farm's chunks, renewed every loader interval (100 ticks) and kept for three of them. */
	public static final Supplier<ChunkTicketType> FARM = register("farm", 300);

	private ModTicketTypes() {
	}

	public static void register() {
		TheDreamTableMod.LOGGER.debug("Registered chunk ticket types");
	}

	private static Supplier<ChunkTicketType> register(String path, long expiryTicks) {
		return Platform.content().register(
				RegistryKeys.TICKET_TYPE,
				Identifier.of(TheDreamTableMod.MOD_ID, path),
				() -> new ChunkTicketType(expiryTicks, LOADING_AND_SIMULATION)
		);
	}
}
