package com.davisr.dreamtablepoc;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

public final class CounterState extends PersistentState {
	private static final String SAVE_ID = DreamTablePoc.MOD_ID + "_counter";
	private static final String COUNT_KEY = "count";
	private static final Type<CounterState> TYPE = new Type<>(CounterState::new, CounterState::fromNbt, null);

	private int count;

	public static CounterState get(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, SAVE_ID);
	}

	static CounterState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		CounterState state = new CounterState();
		state.count = nbt.getInt(COUNT_KEY);
		return state;
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		nbt.putInt(COUNT_KEY, count);
		return nbt;
	}

	public int value() {
		return count;
	}

	public int increment() {
		count++;
		markDirty();
		return count;
	}
}
