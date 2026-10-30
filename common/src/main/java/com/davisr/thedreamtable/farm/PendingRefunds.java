package com.davisr.thedreamtable.farm;

import com.davisr.thedreamtable.platform.Platform;
import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Materials owed to a farm's owner who was offline when a Take Down refunded the steps they had paid for. They
 * are kept in the overworld by item id and handed over, once, as soon as the player is online and alive, wherever they are.
 * An item this game no longer knows stays on record instead of being lost.
 */
public final class PendingRefunds extends PersistentState {
	static final String STATE_KEY = "the_dream_table_pending_refunds";
	private static final Type<PendingRefunds> TYPE = new Type<>(PendingRefunds::new, PendingRefunds::read, null);
	private static final int CHECK_INTERVAL_TICKS = 40;
	static final int MAX_PLAYERS = 4096;
	static final int MAX_KINDS_PER_PLAYER = 256;

	private final Map<UUID, Map<String, Integer>> owed = new LinkedHashMap<>();

	public static PendingRefunds of(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, STATE_KEY);
	}

	public static void register() {
		Platform.serverEvents().onTickEnd(server -> {
			if (server.getTicks() % CHECK_INTERVAL_TICKS != 0) {
				return;
			}
			PendingRefunds refunds = of(server);
			if (refunds.owed.isEmpty()) {
				return;
			}
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				if (player.isAlive() && refunds.owed.containsKey(player.getUuid())) {
					refunds.deliver(player);
				}
			}
		});
	}

	/** Keeps {@code count} of the item for the player. Returns false, keeping nothing, when the limits are reached. */
	public boolean add(UUID player, Item item, int count) {
		return add(player, Registries.ITEM.getId(item).toString(), count);
	}

	boolean add(UUID player, String itemId, int count) {
		if (count <= 0) {
			return true;
		}
		Map<String, Integer> items = owed.get(player);
		if (items == null) {
			if (owed.size() >= MAX_PLAYERS) {
				return false;
			}
			items = new LinkedHashMap<>();
		} else if (!items.containsKey(itemId) && items.size() >= MAX_KINDS_PER_PLAYER) {
			return false;
		}
		items.merge(itemId, count, (first, second) -> (int) Math.min(Integer.MAX_VALUE, (long) first + second));
		owed.put(player, items);
		markDirty();
		return true;
	}

	/** What is owed to the player, by item id. */
	public Map<String, Integer> owedTo(UUID player) {
		return Map.copyOf(owed.getOrDefault(player, Map.of()));
	}

	/** Hands the player what is owed and forgets it before giving, so it is never given twice. Unknown items stay owed. */
	void deliver(ServerPlayerEntity player) {
		Map<String, Integer> items = owed.get(player.getUuid());
		if (items == null) {
			return;
		}
		Map<Item, Integer> now = new LinkedHashMap<>();
		Iterator<Map.Entry<String, Integer>> entries = items.entrySet().iterator();
		while (entries.hasNext()) {
			Map.Entry<String, Integer> entry = entries.next();
			Identifier id = Identifier.tryParse(entry.getKey());
			Item item = id == null ? null : Registries.ITEM.getOrEmpty(id).orElse(null);
			if (item != null) {
				now.put(item, entry.getValue());
				entries.remove();
			}
		}
		if (items.isEmpty()) {
			owed.remove(player.getUuid());
		}
		if (now.isEmpty()) {
			return;
		}
		markDirty();
		boolean dropped = false;
		for (Map.Entry<Item, Integer> entry : now.entrySet()) {
			dropped |= Handover.giveAll(player, entry.getKey(), entry.getValue());
		}
		player.sendMessage(Text.translatable("gui.the_dream_table.refund_waiting"), false);
		if (dropped) {
			Handover.tellDropped(player);
		}
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		NbtCompound players = new NbtCompound();
		for (Map.Entry<UUID, Map<String, Integer>> entry : owed.entrySet()) {
			NbtCompound items = new NbtCompound();
			entry.getValue().forEach(items::putInt);
			players.put(entry.getKey().toString(), items);
		}
		nbt.put("Players", players);
		return nbt;
	}

	/** Reads at most the limits of players and item kinds, so a corrupt file stays bounded; malformed entries are skipped. */
	static PendingRefunds read(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		PendingRefunds refunds = new PendingRefunds();
		NbtCompound players = nbt.getCompound("Players");
		for (String player : players.getKeys()) {
			UUID id;
			try {
				id = UUID.fromString(player);
			} catch (IllegalArgumentException malformed) {
				continue;
			}
			NbtCompound saved = players.getCompound(player);
			Map<String, Integer> items = new HashMap<>();
			for (String item : saved.getKeys()) {
				if (items.size() < MAX_KINDS_PER_PLAYER && saved.contains(item, NbtElement.INT_TYPE) && saved.getInt(item) > 0) {
					items.put(item, saved.getInt(item));
				}
			}
			if (!items.isEmpty() && refunds.owed.size() < MAX_PLAYERS) {
				refunds.owed.put(id, items);
			}
		}
		return refunds;
	}
}
