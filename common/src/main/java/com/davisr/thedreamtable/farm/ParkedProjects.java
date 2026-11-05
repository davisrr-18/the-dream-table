package com.davisr.thedreamtable.farm;

import com.davisr.thedreamtable.block.entity.TableSession;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.world.PersistentState;
import org.jetbrains.annotations.Nullable;

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
 * A kept build also reserves its farm's area in the world it stands in ({@link #reserves}) until it is restored, so no other
 * farm can clear its blocks while it waits. The area is derived from the saved spot; nothing extra is written.
 */
public final class ParkedProjects extends PersistentState {
	static final String STATE_KEY = "the_dream_table_parked_projects";
	private static final Type<ParkedProjects> TYPE = new Type<>(ParkedProjects::new, ParkedProjects::read, null);
	static final int MAX_PLAYERS = 4096;
	static final int MAX_PER_PLAYER = 16;

	/** A kept build's area: the world it stands in and the box its farm occupies there. */
	public record Reservation(Identifier world, Box box) {
	}

	private final Map<UUID, List<NbtCompound>> parked = new LinkedHashMap<>();
	/** The reservations of {@link #parked}, derived on first use and dropped after any change to what is kept. */
	private @Nullable List<Reservation> reservations;

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
		reservations = null;
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
				reservations = null;
				markDirty();
				return true;
			}
		}
		return false;
	}

	/** Whether a kept build in {@code world} reserves a box the test accepts. */
	public boolean reserves(Identifier world, Predicate<Box> test) {
		for (Reservation reservation : reservations()) {
			if (reservation.world().equals(world) && test.test(reservation.box())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The areas of every kept build, cached until the kept projects change.
	 * shortcut: a build whose farm is not loaded at the first check reserves nothing until the next park or restore, upgrade if farms can load after server start.
	 */
	private List<Reservation> reservations() {
		if (reservations == null) {
			List<Reservation> found = new ArrayList<>();
			for (List<NbtCompound> projects : parked.values()) {
				for (NbtCompound project : projects) {
					Reservation reservation = TableSession.keptReservation(project);
					if (reservation != null) {
						found.add(reservation);
					}
				}
			}
			reservations = List.copyOf(found);
		}
		return reservations;
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
