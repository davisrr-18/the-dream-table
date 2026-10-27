package com.davisr.thedreamtable.farm;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Atomic farm metadata changes used by authenticated server handlers. */
public final class FarmSharing {
	private static final int CLEANUP_INTERVAL_TICKS = 1200;

	public enum RenameResult {
		SUCCESS,
		NOT_FOUND,
		FORBIDDEN,
		INVALID_INPUT
	}

	public enum InviteResult {
		SUCCESS,
		NOT_FOUND,
		FORBIDDEN,
		SELF,
		DUPLICATE,
		INVALID_ROLE,
		COLLABORATOR_LIMIT,
		PENDING_LIMIT
	}

	public enum AcceptResult {
		SUCCESS,
		NOT_FOUND,
		EXPIRED,
		DUPLICATE,
		COLLABORATOR_LIMIT
	}

	public record InviteOutcome(InviteResult result, @Nullable FarmInvite invite) {
	}

	private FarmSharing() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTicks() % CLEANUP_INTERVAL_TICKS == 0) {
				long now = System.currentTimeMillis();
				server.getWorlds().forEach(world -> cleanupExpired(FarmAreas.of(world), now));
			}
		});
	}

	public static RenameResult rename(FarmAreas areas, UUID instanceId, UUID ownerUuid, String input) {
		FarmAreas.Finished farm = areas.finished(instanceId).orElse(null);
		if (farm == null) {
			return RenameResult.NOT_FOUND;
		}
		if (!farm.ownerUuid().equals(ownerUuid)) {
			return RenameResult.FORBIDDEN;
		}
		FarmNames.Validation validation = FarmNames.validate(input);
		if (validation.result() != FarmNames.Result.VALID && validation.result() != FarmNames.Result.RESET) {
			return RenameResult.INVALID_INPUT;
		}
		areas.setCustomName(instanceId, validation.name());
		return RenameResult.SUCCESS;
	}

	public static InviteOutcome invite(FarmAreas areas, UUID instanceId, UUID ownerUuid, UUID playerUuid,
			FarmRole role, long now, int maxCollaborators, int maxPendingInvites, int pendingForPlayer) {
		areas.removeExpiredInvites(now);
		FarmAreas.Finished farm = areas.finished(instanceId).orElse(null);
		if (farm == null) {
			return new InviteOutcome(InviteResult.NOT_FOUND, null);
		}
		if (!farm.ownerUuid().equals(ownerUuid)) {
			return new InviteOutcome(InviteResult.FORBIDDEN, null);
		}
		if (ownerUuid.equals(playerUuid)) {
			return new InviteOutcome(InviteResult.SELF, null);
		}
		if (role == null || role == FarmRole.OWNER) {
			return new InviteOutcome(InviteResult.INVALID_ROLE, null);
		}
		if (farm.grants().containsKey(playerUuid) || farm.invites().values().stream().anyMatch(invite -> invite.playerUuid().equals(playerUuid))) {
			return new InviteOutcome(InviteResult.DUPLICATE, null);
		}
		if (farm.grants().size() >= Math.max(0, maxCollaborators)) {
			return new InviteOutcome(InviteResult.COLLABORATOR_LIMIT, null);
		}
		if (pendingForPlayer >= Math.max(0, maxPendingInvites)) {
			return new InviteOutcome(InviteResult.PENDING_LIMIT, null);
		}
		if (farm.invites().size() >= FarmInvite.MAX_SAVED_INVITES) {
			return new InviteOutcome(InviteResult.PENDING_LIMIT, null);
		}
		FarmInvite invite = FarmInvite.create(playerUuid, role, now);
		areas.putInvite(instanceId, invite);
		return new InviteOutcome(InviteResult.SUCCESS, invite);
	}

	public static AcceptResult accept(FarmAreas areas, UUID instanceId, UUID playerUuid, UUID inviteId, long now,
			int maxCollaborators) {
		FarmAreas.Finished farm = areas.finished(instanceId).orElse(null);
		if (farm == null) {
			return AcceptResult.NOT_FOUND;
		}
		FarmInvite invite = farm.invites().get(inviteId);
		if (invite == null || !invite.playerUuid().equals(playerUuid)) {
			return AcceptResult.NOT_FOUND;
		}
		if (invite.expired(now)) {
			return AcceptResult.EXPIRED;
		}
		if (farm.grants().containsKey(playerUuid)) {
			return AcceptResult.DUPLICATE;
		}
		if (farm.grants().size() >= Math.max(0, maxCollaborators)) {
			return AcceptResult.COLLABORATOR_LIMIT;
		}
		areas.acceptInvite(instanceId, inviteId, new FarmGrant(playerUuid, invite.role(), now));
		return AcceptResult.SUCCESS;
	}

	public static int cleanupExpired(FarmAreas areas, long now) {
		return areas.removeExpiredInvites(now);
	}

	public static int pendingInvites(Iterable<FarmAreas> states, UUID playerUuid, long now) {
		int count = 0;
		for (FarmAreas state : states) {
			count += state.pendingInvites(playerUuid, now);
		}
		return count;
	}
}
