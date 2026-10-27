package com.davisr.thedreamtable.farm;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

import java.util.Objects;
import java.util.UUID;

/** Public immutable identity for one finished farm, independent of its table or position. */
public record FarmKey(Identifier dimension, UUID instanceId) {
	public FarmKey {
		Objects.requireNonNull(dimension, "dimension");
		Objects.requireNonNull(instanceId, "instanceId");
	}

	public static FarmKey of(ServerWorld world, UUID instanceId) {
		Objects.requireNonNull(world, "world");
		return new FarmKey(world.getRegistryKey().getValue(), instanceId);
	}

	public String externalForm() {
		return dimension + "|" + instanceId;
	}

	public static FarmKey parse(String value) {
		if (value == null) {
			throw new IllegalArgumentException("Farm key cannot be null");
		}
		int separator = value.indexOf('|');
		if (separator <= 0 || separator != value.lastIndexOf('|') || separator == value.length() - 1) {
			throw new IllegalArgumentException("Invalid farm key: " + value);
		}
		Identifier dimension = Identifier.tryParse(value.substring(0, separator));
		if (dimension == null) {
			throw new IllegalArgumentException("Invalid farm dimension: " + value.substring(0, separator));
		}
		try {
			return new FarmKey(dimension, UUID.fromString(value.substring(separator + 1)));
		} catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException("Invalid farm instance id", exception);
		}
	}
}
