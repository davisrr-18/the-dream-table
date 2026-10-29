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

/** Resolves public farm identities and applies server-derived authorization before returning records. */
public final class FarmDirectory {
	private FarmDirectory() {
	}

	public record Located(FarmKey key, ServerWorld world, FarmAreas.Finished farm, FarmRecord record) {
	}

	public record Authorized(Located located, FarmRole role) {
	}

	public static Optional<Located> resolve(MinecraftServer server, FarmKey key) {
		RegistryKey<World> worldKey = RegistryKey.of(RegistryKeys.WORLD, key.dimension());
		ServerWorld world = server.getWorld(worldKey);
		if (world == null) {
			return Optional.empty();
		}
		return FarmAreas.of(world).finished(key.instanceId()).map(farm -> located(key, world, farm));
	}

	public static Optional<Authorized> authorize(MinecraftServer server, FarmKey key, UUID actorUuid, FarmAction action, boolean sharingEnabled) {
		return resolve(server, key).flatMap(located -> FarmAccess.role(located.record(), actorUuid, sharingEnabled)
				.filter(role -> FarmAccess.allows(role, action))
				.map(role -> new Authorized(located, role)));
	}

	public static List<Authorized> visibleTo(MinecraftServer server, UUID actorUuid, boolean sharingEnabled) {
		List<Authorized> farms = new ArrayList<>();
		for (ServerWorld world : server.getWorlds()) {
			for (FarmAreas.Finished farm : FarmAreas.of(world).finished()) {
				FarmKey key = FarmKey.of(world, farm.instanceId());
				Located located = located(key, world, farm);
				FarmAccess.role(located.record(), actorUuid, sharingEnabled)
						.filter(role -> FarmAccess.allows(role, FarmAction.VIEW))
						.ifPresent(role -> farms.add(new Authorized(located, role)));
			}
		}
		return List.copyOf(farms);
	}

	private static Located located(FarmKey key, ServerWorld world, FarmAreas.Finished farm) {
		FarmRecord record = record(key, farm);
		return new Located(key, world, farm, record);
	}

	static FarmRecord record(FarmKey key, FarmAreas.Finished farm) {
		return new FarmRecord(key, farm.ownerUuid(), farm.grants());
	}
}
