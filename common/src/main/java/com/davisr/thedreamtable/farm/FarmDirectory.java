package com.davisr.thedreamtable.farm;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Resolves public farm identities and applies the owner check before returning records. */
public final class FarmDirectory {
	private FarmDirectory() {
	}

	public record Located(FarmKey key, ServerWorld world, FarmAreas.Finished farm, FarmRecord record) {
	}

	public static Optional<Located> resolve(MinecraftServer server, FarmKey key) {
		RegistryKey<World> worldKey = RegistryKey.of(RegistryKeys.WORLD, key.dimension());
		ServerWorld world = server.getWorld(worldKey);
		if (world == null) {
			return Optional.empty();
		}
		return FarmAreas.of(world).finished(key.instanceId()).map(farm -> located(key, world, farm));
	}

	/** Every finished farm the player owns, in every world. */
	public static List<Located> ownedBy(MinecraftServer server, UUID actorUuid) {
		List<Located> farms = new ArrayList<>();
		for (ServerWorld world : server.getWorlds()) {
			for (FarmAreas.Finished farm : FarmAreas.of(world).finishedBy(actorUuid)) {
				farms.add(located(FarmKey.of(world, farm.instanceId()), world, farm));
			}
		}
		return List.copyOf(farms);
	}

	private static Located located(FarmKey key, ServerWorld world, FarmAreas.Finished farm) {
		return new Located(key, world, farm, record(key, farm));
	}

	static FarmRecord record(FarmKey key, FarmAreas.Finished farm) {
		return new FarmRecord(key, farm.ownerUuid());
	}
}
