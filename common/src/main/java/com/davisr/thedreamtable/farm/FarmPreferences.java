package com.davisr.thedreamtable.farm;

import com.davisr.thedreamtable.platform.Platform;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;

/** Per-player farm UI preferences, stored once in the overworld. */
public final class FarmPreferences extends PersistentState {
	static final String STATE_KEY = "the_dream_table_farm_preferences";
	private static final int PRUNE_INTERVAL_TICKS = 1200;
	static final int MAX_SAVED_PLAYERS = 4096;
	static final int MAX_SAVED_FARMS_PER_PLAYER = 4096;

	private final Map<UUID, PlayerPreferences> players = new HashMap<>();

	private record PlayerPreferences(Set<FarmKey> favorites, Map<FarmKey, Long> acknowledgements) {
		private PlayerPreferences() {
			this(new LinkedHashSet<>(), new HashMap<>());
		}
	}

	private record Relationship(UUID playerUuid, FarmKey farmKey) {
	}

	private final Map<UUID, FarmKey> tracked = new HashMap<>();
	/** Players who turned on "Protect My Farms". Absent means off, so worlds saved before the option read as off. */
	private final Set<UUID> protecting = new LinkedHashSet<>();

	public static FarmPreferences of(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(nbt -> read(nbt, null), FarmPreferences::new, STATE_KEY);
	}

	public static void register() {
		Platform.serverEvents().onTickEnd(server -> {
			if (server.getTicks() % PRUNE_INTERVAL_TICKS == 0) {
				FarmPreferences preferences = of(server);
				preferences.prune((player, key) -> FarmDirectory.resolve(server, key)
						.map(located -> FarmAccess.isOwner(located.record(), player))
						.orElse(false));
			}
		});
	}

	public boolean isFavorite(UUID playerUuid, FarmKey key) {
		PlayerPreferences preferences = players.get(playerUuid);
		return preferences != null && preferences.favorites().contains(key);
	}

	public boolean setFavorite(UUID playerUuid, FarmKey key, boolean favorite) {
		PlayerPreferences preferences = players.get(playerUuid);
		if (preferences == null) {
			if (!favorite) {
				return true;
			}
			if (!canAdd(playerUuid, key)) {
				return false;
			}
			preferences = new PlayerPreferences();
			players.put(playerUuid, preferences);
		} else if (favorite && !canAdd(playerUuid, key)) {
			return false;
		}
		boolean changed = favorite ? preferences.favorites().add(key) : preferences.favorites().remove(key);
		if (changed) {
			markDirty();
			removeEmpty(playerUuid, preferences);
		}
		return true;
	}

	/** Whether the player asked other players to be kept from breaking the structure of their finished farms. Default off. */
	public boolean protectsFarms(UUID playerUuid) {
		return protecting.contains(playerUuid);
	}

	/** Turns "Protect My Farms" on or off; refuses only to add a player beyond the saved-player limit. */
	public boolean setProtectsFarms(UUID playerUuid, boolean protect) {
		return setMember(protecting, playerUuid, protect);
	}

	private boolean setMember(Set<UUID> members, UUID playerUuid, boolean member) {
		if (member) {
			if (!members.contains(playerUuid) && members.size() >= MAX_SAVED_PLAYERS) {
				return false;
			}
			if (members.add(playerUuid)) {
				markDirty();
			}
		} else if (members.remove(playerUuid)) {
			markDirty();
		}
		return true;
	}

	public boolean track(UUID playerUuid, FarmKey key) {
		if (!canTrack(playerUuid, key)) {
			return false;
		}
		if (!key.equals(tracked.put(playerUuid, key))) {
			markDirty();
		}
		return true;
	}

	public void clearTracking(UUID playerUuid) {
		if (tracked.remove(playerUuid) != null) {
			markDirty();
		}
	}

	public Optional<FarmKey> tracked(UUID playerUuid) {
		return Optional.ofNullable(tracked.get(playerUuid));
	}

	public boolean acknowledge(UUID playerUuid, FarmKey key, long cursor) {
		if (cursor <= acknowledgement(playerUuid, key)) {
			return true;
		}
		if (!canAdd(playerUuid, key)) {
			return false;
		}
		players.computeIfAbsent(playerUuid, ignored -> new PlayerPreferences()).acknowledgements().put(key, cursor);
		markDirty();
		return true;
	}

	public long acknowledgement(UUID playerUuid, FarmKey key) {
		PlayerPreferences preferences = players.get(playerUuid);
		return preferences == null ? 0L : preferences.acknowledgements().getOrDefault(key, 0L);
	}

	public void removeAccess(UUID playerUuid, FarmKey key) {
		boolean changed = false;
		PlayerPreferences preferences = players.get(playerUuid);
		if (preferences != null) {
			changed |= preferences.favorites().remove(key);
			changed |= preferences.acknowledgements().remove(key) != null;
			removeEmpty(playerUuid, preferences);
		}
		if (key.equals(tracked.get(playerUuid))) {
			tracked.remove(playerUuid);
			changed = true;
		}
		if (changed) {
			markDirty();
		}
	}

	public void removeFarm(FarmKey key) {
		for (UUID playerUuid : relationshipPlayers(key)) {
			removeAccess(playerUuid, key);
		}
	}

	/** Removes one inaccessible player/farm relationship regardless of how many preference fields it occupied. */
	public int prune(BiPredicate<UUID, FarmKey> accessible) {
		List<Relationship> stale = new ArrayList<>();
		for (UUID playerUuid : allPlayers()) {
			for (FarmKey key : relationshipKeys(playerUuid)) {
				if (!accessible.test(playerUuid, key)) {
					stale.add(new Relationship(playerUuid, key));
				}
			}
		}
		stale.forEach(relationship -> removeAccess(relationship.playerUuid(), relationship.farmKey()));
		return stale.size();
	}

	private Set<UUID> relationshipPlayers(FarmKey key) {
		Set<UUID> result = new LinkedHashSet<>();
		for (Map.Entry<UUID, PlayerPreferences> entry : players.entrySet()) {
			if (entry.getValue().favorites().contains(key) || entry.getValue().acknowledgements().containsKey(key)) {
				result.add(entry.getKey());
			}
		}
		for (Map.Entry<UUID, FarmKey> entry : tracked.entrySet()) {
			if (entry.getValue().equals(key)) {
				result.add(entry.getKey());
			}
		}
		return result;
	}

	private Set<UUID> allPlayers() {
		Set<UUID> result = new LinkedHashSet<>(players.keySet());
		result.addAll(tracked.keySet());
		return result;
	}

	private Set<FarmKey> relationshipKeys(UUID playerUuid) {
		Set<FarmKey> result = new LinkedHashSet<>();
		PlayerPreferences preferences = players.get(playerUuid);
		if (preferences != null) {
			result.addAll(preferences.favorites());
			result.addAll(preferences.acknowledgements().keySet());
		}
		FarmKey trackedKey = tracked.get(playerUuid);
		if (trackedKey != null) {
			result.add(trackedKey);
		}
		return result;
	}

	private void removeEmpty(UUID playerUuid, PlayerPreferences preferences) {
		if (preferences.favorites().isEmpty() && preferences.acknowledgements().isEmpty()) {
			players.remove(playerUuid);
		}
	}

	private boolean canAdd(UUID playerUuid, FarmKey key) {
		Set<FarmKey> existing = relationshipKeys(playerUuid);
		if (existing.contains(key)) {
			return true;
		}
		if (!allPlayers().contains(playerUuid) && allPlayers().size() >= MAX_SAVED_PLAYERS) {
			return false;
		}
		return existing.size() < MAX_SAVED_FARMS_PER_PLAYER;
	}

	private boolean canTrack(UUID playerUuid, FarmKey key) {
		FarmKey current = tracked.get(playerUuid);
		if (key.equals(current)) {
			return true;
		}
		Set<FarmKey> existing = relationshipKeys(playerUuid);
		PlayerPreferences preferences = players.get(playerUuid);
		if (current != null && (preferences == null
				|| !preferences.favorites().contains(current) && !preferences.acknowledgements().containsKey(current))) {
			existing.remove(current);
		}
		if (existing.contains(key)) {
			return true;
		}
		if (!allPlayers().contains(playerUuid) && allPlayers().size() >= MAX_SAVED_PLAYERS) {
			return false;
		}
		return existing.size() < MAX_SAVED_FARMS_PER_PLAYER;
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt) {
		NbtList list = new NbtList();
		for (UUID playerUuid : allPlayers()) {
			NbtCompound item = new NbtCompound();
			item.putUuid("Player", playerUuid);
			PlayerPreferences preferences = players.get(playerUuid);
			NbtList favorites = new NbtList();
			if (preferences != null) {
				preferences.favorites().stream()
						.map(FarmKey::externalForm).map(NbtString::of).forEach(favorites::add);
			}
			item.put("Favorites", favorites);
			FarmKey trackedKey = tracked.get(playerUuid);
			if (trackedKey != null) {
				item.putString("Tracked", trackedKey.externalForm());
			}
			NbtList cursors = new NbtList();
			if (preferences != null) {
				preferences.acknowledgements().entrySet().forEach(entry -> {
					NbtCompound cursor = new NbtCompound();
					cursor.putString("Farm", entry.getKey().externalForm());
					cursor.putLong("Cursor", entry.getValue());
					cursors.add(cursor);
				});
			}
			item.put("Acknowledgements", cursors);
			list.add(item);
		}
		nbt.put("Players", list);
		writePlayers(nbt, "Protecting", protecting);
		return nbt;
	}

	private static void writePlayers(NbtCompound nbt, String key, Set<UUID> members) {
		if (members.isEmpty()) {
			return;
		}
		NbtList list = new NbtList();
		for (UUID playerUuid : members) {
			NbtCompound item = new NbtCompound();
			item.putUuid("Player", playerUuid);
			list.add(item);
		}
		nbt.put(key, list);
	}

	private static void readPlayers(NbtCompound nbt, String key, Set<UUID> members) {
		NbtList list = nbt.getList(key, NbtElement.COMPOUND_TYPE);
		for (int index = 0; index < Math.min(list.size(), MAX_SAVED_PLAYERS); index++) {
			NbtCompound item = list.getCompound(index);
			if (item.containsUuid("Player")) {
				members.add(item.getUuid("Player"));
			}
		}
	}

	static FarmPreferences read(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		FarmPreferences state = new FarmPreferences();
		NbtList list = nbt.getList("Players", NbtElement.COMPOUND_TYPE);
		for (int index = 0; index < Math.min(list.size(), MAX_SAVED_PLAYERS); index++) {
			NbtCompound item = list.getCompound(index);
			if (!item.containsUuid("Player")) {
				continue;
			}
			UUID playerUuid = item.getUuid("Player");
			PlayerPreferences preferences = new PlayerPreferences();
			NbtList favorites = item.getList("Favorites", NbtElement.STRING_TYPE);
			for (int favorite = 0; favorite < Math.min(favorites.size(), MAX_SAVED_FARMS_PER_PLAYER); favorite++) {
				parseKey(favorites.getString(favorite)).ifPresent(preferences.favorites()::add);
			}
			NbtList cursors = item.getList("Acknowledgements", NbtElement.COMPOUND_TYPE);
			for (int cursor = 0; cursor < Math.min(cursors.size(), MAX_SAVED_FARMS_PER_PLAYER); cursor++) {
				NbtCompound saved = cursors.getCompound(cursor);
				long value = saved.getLong("Cursor");
				if (value > 0) {
					parseKey(saved.getString("Farm")).ifPresent(key -> {
						if (preferences.favorites().contains(key) || preferences.acknowledgements().containsKey(key)
								|| relationshipCount(preferences) < MAX_SAVED_FARMS_PER_PLAYER) {
							preferences.acknowledgements().merge(key, value, Math::max);
						}
					});
				}
			}
			if (!preferences.favorites().isEmpty() || !preferences.acknowledgements().isEmpty()) {
				state.players.put(playerUuid, preferences);
			}
			parseKey(item.getString("Tracked")).ifPresent(key -> {
				if (preferences.favorites().contains(key) || preferences.acknowledgements().containsKey(key)
						|| relationshipCount(preferences) < MAX_SAVED_FARMS_PER_PLAYER) {
					state.tracked.put(playerUuid, key);
				}
			});
		}
		readPlayers(nbt, "Protecting", state.protecting);
		// "ViewersBreak" and "DecliningInvites" from earlier versions are not read and are dropped on the next save.
		return state;
	}

	private static Optional<FarmKey> parseKey(String value) {
		try {
			return Optional.of(FarmKey.parse(value));
		} catch (IllegalArgumentException malformed) {
			return Optional.empty();
		}
	}

	private static int relationshipCount(PlayerPreferences preferences) {
		Set<FarmKey> keys = new LinkedHashSet<>(preferences.favorites());
		keys.addAll(preferences.acknowledgements().keySet());
		return keys.size();
	}
}
