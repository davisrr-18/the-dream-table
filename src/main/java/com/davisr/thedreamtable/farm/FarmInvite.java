package com.davisr.thedreamtable.farm;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** A pending invitation whose authority comes from its stored player and role. */
public record FarmInvite(UUID inviteId, UUID playerUuid, String playerName, FarmRole role, long createdAt, long expiresAt) {
	public static final long LIFETIME_MILLIS = Duration.ofDays(7).toMillis();
	static final int MAX_SAVED_INVITES = 1024;

	public FarmInvite {
		Objects.requireNonNull(inviteId, "inviteId");
		Objects.requireNonNull(playerUuid, "playerUuid");
		Objects.requireNonNull(playerName, "playerName");
		Objects.requireNonNull(role, "role");
		if (playerName.codePointCount(0, playerName.length()) > 64 || playerName.codePoints().anyMatch(codePoint ->
				Character.isISOControl(codePoint) || codePoint == '\u00a7' || codePoint <= Character.MAX_VALUE && Character.isSurrogate((char) codePoint))) {
			throw new IllegalArgumentException("Invitation display name is invalid");
		}
		if (role == FarmRole.OWNER) {
			throw new IllegalArgumentException("Ownership cannot be invited");
		}
		long expectedExpiry;
		try {
			expectedExpiry = Math.addExact(createdAt, LIFETIME_MILLIS);
		} catch (ArithmeticException overflow) {
			throw new IllegalArgumentException("Invitation time is outside the supported range", overflow);
		}
		if (expiresAt != expectedExpiry) {
			throw new IllegalArgumentException("Invitations must expire after exactly seven days");
		}
	}

	public FarmInvite(UUID inviteId, UUID playerUuid, FarmRole role, long createdAt, long expiresAt) {
		this(inviteId, playerUuid, "", role, createdAt, expiresAt);
	}

	public static FarmInvite create(UUID playerUuid, FarmRole role, long now) {
		return create(playerUuid, "", role, now);
	}

	public static FarmInvite create(UUID playerUuid, String playerName, FarmRole role, long now) {
		return new FarmInvite(UUID.randomUUID(), playerUuid, playerName, role, now, Math.addExact(now, LIFETIME_MILLIS));
	}

	public boolean expired(long now) {
		return now >= expiresAt;
	}

	NbtCompound write() {
		NbtCompound nbt = new NbtCompound();
		nbt.putUuid("InviteId", inviteId);
		nbt.putUuid("Player", playerUuid);
		if (!playerName.isEmpty()) {
			nbt.putString("PlayerName", playerName);
		}
		nbt.putString("Role", role.name());
		nbt.putLong("CreatedAt", createdAt);
		nbt.putLong("ExpiresAt", expiresAt);
		return nbt;
	}

	static FarmInvite read(NbtCompound nbt) {
		if (!nbt.containsUuid("InviteId") || !nbt.containsUuid("Player")) {
			throw new IllegalArgumentException("Invitation identity is missing");
		}
		return new FarmInvite(nbt.getUuid("InviteId"), nbt.getUuid("Player"), nbt.getString("PlayerName"), FarmRole.valueOf(nbt.getString("Role")),
				nbt.getLong("CreatedAt"), nbt.getLong("ExpiresAt"));
	}

	public static void writeAll(NbtCompound nbt, String key, Map<UUID, FarmInvite> invites) {
		NbtList list = new NbtList();
		invites.values().stream().limit(MAX_SAVED_INVITES).map(FarmInvite::write).forEach(list::add);
		nbt.put(key, list);
	}

	public static Map<UUID, FarmInvite> readAll(NbtCompound nbt, String key) {
		Map<UUID, FarmInvite> invites = new LinkedHashMap<>();
		NbtList list = nbt.getList(key, NbtElement.COMPOUND_TYPE);
		for (int index = 0; index < Math.min(list.size(), MAX_SAVED_INVITES); index++) {
			try {
				FarmInvite invite = read(list.getCompound(index));
				boolean duplicatePlayer = invites.values().stream()
						.anyMatch(existing -> existing.playerUuid().equals(invite.playerUuid()));
				if (!duplicatePlayer) {
					invites.putIfAbsent(invite.inviteId(), invite);
				}
			} catch (IllegalArgumentException malformed) {
				// Ignore invalid external save data without weakening valid invitations.
			}
		}
		return Map.copyOf(invites);
	}
}
