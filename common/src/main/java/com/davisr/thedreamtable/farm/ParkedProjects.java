package com.davisr.thedreamtable.farm;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Projects kept for their players when a Dream Table is broken: each player's saved session as the table wrote it, oldest
 * first, kept in the overworld until the player opens a table that holds no project of theirs. Entries are kept as saved
 * and only read when they are restored, so one this version cannot read stays on record for a later version.
 */
public final class ParkedProjects extends PersistentState {
	static final String STATE_KEY = "the_dream_table_parked_projects";
	private static final Type<ParkedProjects> TYPE = new Type<>(ParkedProjects::new, ParkedProjects::read, null);
	static final int MAX_PLAYERS = 4096;
	static final int MAX_PER_PLAYER = 16;

	private final Map<UUID, List<NbtCompound>> parked = new LinkedHashMap<>();

	public static ParkedProjects of(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, STATE_KEY);
	}

	/** Whether one more project can be kept for the player within the limits. */
	public boolean canPark(UUID player) {
		List<NbtCompound> projects = parked.get(player);
		return projects == null ? parked.size() < MAX_PLAYERS : projects.size() < MAX_PER_PLAYER;
	}

	/** Keeps a copy of the project for the player. Returns false, keeping nothing, when the limits are reached. */
	public boolean park(UUID player, NbtCompound project) {
		if (!canPark(player)) {
			return false;
		}
		List<NbtCompound> projects = parked.computeIfAbsent(player, ignored -> new ArrayList<>());
		projects.add(project.copy());
		markDirty();
		return true;
	}

	/** How many projects are kept for the player. */
	public int count(UUID player) {
		return parked.getOrDefault(player, List.of()).size();
	}

	/**
	 * Offers copies of the player's projects, oldest first, to {@code taker} until it takes one, which is then forgotten.
	 * One it refuses, such as one it cannot read, stays as saved. Returns whether one was taken.
	 */
	public boolean restore(UUID player, Predicate<NbtCompound> taker) {
		List<NbtCompound> projects = parked.get(player);
		if (projects == null) {
			return false;
		}
		Iterator<NbtCompound> entries = projects.iterator();
		while (entries.hasNext()) {
			if (taker.test(entries.next().copy())) {
				entries.remove();
				if (projects.isEmpty()) {
					parked.remove(player);
				}
				markDirty();
				return true;
			}
		}
		return false;
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		NbtCompound players = new NbtCompound();
		for (Map.Entry<UUID, List<NbtCompound>> entry : parked.entrySet()) {
			NbtList projects = new NbtList();
			entry.getValue().forEach(project -> projects.add(project.copy()));
			players.put(entry.getKey().toString(), projects);
		}
		nbt.put("Players", players);
		return nbt;
	}

	/** Reads at most the limits of players and projects, so a corrupt file stays bounded; malformed player ids are skipped. */
	static ParkedProjects read(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		ParkedProjects state = new ParkedProjects();
		NbtCompound players = nbt.getCompound("Players");
		for (String player : players.getKeys()) {
			UUID id;
			try {
				id = UUID.fromString(player);
			} catch (IllegalArgumentException malformed) {
				continue;
			}
			NbtList saved = players.getList(player, NbtElement.COMPOUND_TYPE);
			List<NbtCompound> projects = new ArrayList<>();
			for (int index = 0; index < saved.size() && projects.size() < MAX_PER_PLAYER; index++) {
				projects.add(saved.getCompound(index).copy());
			}
			if (!projects.isEmpty() && state.parked.size() < MAX_PLAYERS) {
				state.parked.put(id, projects);
			}
		}
		return state;
	}
}
