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
 * A restored build is held ({@link #hold}) in the table it came back to: it keeps reserving its area for everyone else, and
 * its owner at that table is not blocked by it, until the owner resumes it, discards it, or the table breaks ({@link #release}).
 */
public final class ParkedProjects extends PersistentState {
	static final String STATE_KEY = "the_dream_table_parked_projects";
	static final int MAX_PLAYERS = 4096;
	static final int MAX_PER_PLAYER = 16;
	/** Key for an unreadable saved session that names no player. No player has this id, so nobody is offered its projects, and it reserves nothing. */
	public static final UUID NO_PLAYER = new UUID(0L, 0L);

	/** A kept build's area: the world it stands in and the box its farm occupies there. */
	public record Reservation(Identifier world, Box box) {
	}

	/** The table a restored build is held in: its owner, and the world and position of that table. */
	public record Seat(UUID player, Identifier world, long table) {
	}

	private record Hold(Seat seat, NbtCompound project) {
	}

	/** A reservation and the seat holding it, or null for a parked project. */
	private record Found(@Nullable Seat seat, Reservation reservation) {
	}

	private final Map<UUID, List<NbtCompound>> parked = new LinkedHashMap<>();
	/** Restored builds still waiting for their owner at the table they came back to. Saved under {@code Held}. */
	private final List<Hold> held = new ArrayList<>();
	/** The reservations of {@link #parked} and {@link #held}, derived on first use and dropped after any change to what is kept. */
	private @Nullable List<Found> reservations;
	/** The {@link FarmTemplates#revision} the cached reservations were derived under. */
	private long reservationsRevision;

	public static ParkedProjects of(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(nbt -> read(nbt, null), ParkedProjects::new, STATE_KEY);
	}

	/** Whether one more project can be kept for the player within the limits, held builds included. */
	public boolean canPark(UUID player) {
		if (!parked.containsKey(player) && heldCount(player) == 0) {
			return parked.size() < MAX_PLAYERS;
		}
		return count(player) + heldCount(player) < MAX_PER_PLAYER;
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

	/** How many projects are kept for the player. Held builds are not counted. */
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

	/**
	 * Holds a restored build for the table it came back to. It keeps reserving its area for everyone else until
	 * {@link #release}; its owner at that table is not blocked by it, see {@link #reserves}.
	 */
	public void hold(Seat seat, NbtCompound project) {
		held.add(new Hold(seat, project.copy()));
		reservations = null;
		markDirty();
	}

	/** Lifts the hold of the build restored into this table, when it is resumed, discarded or the table breaks. Nothing is kept by this. */
	public void release(Seat seat) {
		if (held.removeIf(hold -> hold.seat().equals(seat))) {
			reservations = null;
			markDirty();
		}
	}

	private int heldCount(UUID player) {
		int count = 0;
		for (Hold hold : held) {
			if (hold.seat().player().equals(player)) {
				count++;
			}
		}
		return count;
	}

	/** Whether a kept or held build in {@code world} reserves a box the test accepts. */
	public boolean reserves(Identifier world, Predicate<Box> test) {
		return reserves(world, test, null);
	}

	/** As {@link #reserves(Identifier, Predicate)}, except that the build held in {@code own} does not count, so its owner can resume it. */
	public boolean reserves(Identifier world, Predicate<Box> test, @Nullable Seat own) {
		for (Found found : reservations()) {
			Reservation reservation = found.reservation();
			if (reservation.world().equals(world) && test.test(reservation.box()) && (own == null || !own.equals(found.seat()))) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The areas of every kept and held build, cached until the kept projects or the farm designs change. Builds of the
	 * NO_PLAYER entries reserve nothing. A build whose farm is not known yet is not cached, so it is looked up again.
	 */
	private List<Found> reservations() {
		long revision = FarmTemplates.revision();
		if (reservations != null && reservationsRevision == revision) {
			return reservations;
		}
		List<Found> found = new ArrayList<>();
		boolean awaitingTemplate = false;
		for (Map.Entry<UUID, List<NbtCompound>> entry : parked.entrySet()) {
			if (entry.getKey().equals(NO_PLAYER)) {
				continue;
			}
			for (NbtCompound project : entry.getValue()) {
				awaitingTemplate |= add(found, null, project);
			}
		}
		for (Hold hold : held) {
			awaitingTemplate |= add(found, hold.seat(), hold.project());
		}
		List<Found> list = List.copyOf(found);
		if (awaitingTemplate) {
			reservations = null;
		} else {
			reservations = list;
			reservationsRevision = revision;
		}
		return list;
	}

	/** Adds the project's reservation, if it has one. Returns true when it is a build whose farm is not known. */
	private static boolean add(List<Found> found, @Nullable Seat seat, NbtCompound project) {
		Reservation reservation = TableSession.keptReservation(project);
		if (reservation != null) {
			found.add(new Found(seat, reservation));
			return false;
		}
		return project.contains("MovedBuildDimension", NbtElement.STRING_TYPE) && FarmTemplates.get(project.getString("SelectedFarm")) == null;
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt) {
		NbtCompound players = new NbtCompound();
		for (Map.Entry<UUID, List<NbtCompound>> entry : parked.entrySet()) {
			NbtList projects = new NbtList();
			entry.getValue().forEach(project -> projects.add(project.copy()));
			players.put(entry.getKey().toString(), projects);
		}
		nbt.put("Players", players);
		NbtList holds = new NbtList();
		for (Hold hold : held) {
			NbtCompound entry = new NbtCompound();
			entry.putUuid("Player", hold.seat().player());
			entry.putString("World", hold.seat().world().toString());
			entry.putLong("Table", hold.seat().table());
			entry.put("Project", hold.project().copy());
			holds.add(entry);
		}
		nbt.put("Held", holds);
		return nbt;
	}

	/**
	 * Reads at most the limits of players and projects, so a corrupt file stays bounded; malformed player ids are skipped.
	 * A file from before holds had a {@code Held} list reads as holding nothing.
	 */
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
		NbtList holds = nbt.getList("Held", NbtElement.COMPOUND_TYPE);
		for (int index = 0; index < holds.size() && state.held.size() < MAX_PLAYERS; index++) {
			NbtCompound entry = holds.getCompound(index);
			Identifier world = Identifier.tryParse(entry.getString("World"));
			if (world == null || !entry.containsUuid("Player") || !entry.contains("Table", NbtElement.LONG_TYPE)
					|| !entry.contains("Project", NbtElement.COMPOUND_TYPE)) {
				continue;
			}
			state.held.add(new Hold(new Seat(entry.getUuid("Player"), world, entry.getLong("Table")), entry.getCompound("Project").copy()));
		}
		return state;
	}
}
