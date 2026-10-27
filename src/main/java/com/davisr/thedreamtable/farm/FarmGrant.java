package com.davisr.thedreamtable.farm;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** An accepted collaborator grant. Ownership is stored separately on the farm. */
public record FarmGrant(UUID playerUuid, FarmRole role, long acceptedAt) {
	public FarmGrant {
		Objects.requireNonNull(playerUuid, "playerUuid");
		Objects.requireNonNull(role, "role");
		if (role == FarmRole.OWNER) {
			throw new IllegalArgumentException("Ownership cannot be represented as a collaborator grant");
		}
	}

	public static void writeAll(NbtCompound nbt, String key, Map<UUID, FarmGrant> grants) {
		NbtList list = new NbtList();
		for (FarmGrant grant : grants.values()) {
			NbtCompound item = new NbtCompound();
			item.putUuid("Player", grant.playerUuid());
			item.putString("Role", grant.role().name());
			item.putLong("AcceptedAt", grant.acceptedAt());
			list.add(item);
		}
		nbt.put(key, list);
	}

	public static Map<UUID, FarmGrant> readAll(NbtCompound nbt, String key) {
		Map<UUID, FarmGrant> grants = new LinkedHashMap<>();
		NbtList list = nbt.getList(key, NbtElement.COMPOUND_TYPE);
		for (int index = 0; index < list.size(); index++) {
			NbtCompound item = list.getCompound(index);
			if (!item.containsUuid("Player")) {
				continue;
			}
			try {
				UUID player = item.getUuid("Player");
				FarmRole role = FarmRole.valueOf(item.getString("Role"));
				if (role != FarmRole.OWNER) {
					grants.put(player, new FarmGrant(player, role, item.getLong("AcceptedAt")));
				}
			} catch (IllegalArgumentException malformed) {
				// Ignore invalid external save data without weakening valid grants.
			}
		}
		return Map.copyOf(grants);
	}
}
