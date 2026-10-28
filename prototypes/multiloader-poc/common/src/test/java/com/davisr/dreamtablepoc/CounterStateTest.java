package com.davisr.dreamtablepoc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.DynamicRegistryManager;
import org.junit.jupiter.api.Test;

class CounterStateTest {
	// Bootstrap.initialize() cannot run here: Yarn leaves some members package-private across its repackaged
	// packages and only the Fabric runtime fixes that, so a plain JUnit JVM hits IllegalAccessError in SimpleRegistry.
	// Bootstrap-dependent tests belong in the fabric module, which uses fabric-loader-junit.
	@Test
	void roundTripsThroughNbt() {
		CounterState original = new CounterState();
		original.increment();
		original.increment();
		original.increment();

		NbtCompound nbt = original.writeNbt(new NbtCompound(), DynamicRegistryManager.EMPTY);
		CounterState restored = CounterState.fromNbt(nbt, DynamicRegistryManager.EMPTY);

		assertEquals(3, nbt.getInt("count"));
		assertEquals(3, restored.value());
	}
}
