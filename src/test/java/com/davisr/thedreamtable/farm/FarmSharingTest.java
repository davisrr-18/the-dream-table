package com.davisr.thedreamtable.farm;

import com.davisr.thedreamtable.DreamTableConfig;
import com.davisr.thedreamtable.GameSetup;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FarmSharingTest {
	private static final UUID OWNER = UUID.fromString("10000000-0000-0000-0000-000000000001");
	private static final UUID PLAYER = UUID.fromString("20000000-0000-0000-0000-000000000002");
	private static final long NOW = 1_000_000L;

	@BeforeAll
	static void setUp() {
		GameSetup.bootstrap();
	}

	private static FarmAreas farm() {
		FarmAreas areas = new FarmAreas();
		FarmSite site = new FarmSite(new BlockPos(0, 64, 0), new BlockPos(10, 64, 10), Direction.NORTH);
		areas.recordFinished(OWNER, FarmTemplates.get(FarmOptions.SUGAR_CANE.id()), site, Map.of());
		areas.setDirty(false);
		return areas;
	}

	@Test
	void inviteExpiresAtExactlySevenRealDays() {
		FarmInvite invite = FarmInvite.create(PLAYER, FarmRole.OPERATOR, NOW);

		assertEquals(NOW + Duration.ofDays(7).toMillis(), invite.expiresAt());
		assertFalse(invite.expired(invite.expiresAt() - 1));
		assertTrue(invite.expired(invite.expiresAt()));
		long wrappedExpiry = Long.MAX_VALUE + FarmInvite.LIFETIME_MILLIS;
		assertThrows(IllegalArgumentException.class,
				() -> new FarmInvite(UUID.randomUUID(), PLAYER, FarmRole.VIEWER, Long.MAX_VALUE, wrappedExpiry));
	}

	@Test
	void rejectsSelfDuplicateAndOwnerRoles() {
		FarmAreas areas = farm();
		UUID farm = areas.finishedBy(OWNER).getFirst().instanceId();

		assertEquals(FarmSharing.InviteResult.SELF,
				FarmSharing.invite(areas, farm, OWNER, OWNER, FarmRole.VIEWER, NOW, 8, 32, 0).result());
		assertEquals(FarmSharing.InviteResult.INVALID_ROLE,
				FarmSharing.invite(areas, farm, OWNER, PLAYER, FarmRole.OWNER, NOW, 8, 32, 0).result());
		assertEquals(FarmSharing.InviteResult.SUCCESS,
				FarmSharing.invite(areas, farm, OWNER, PLAYER, FarmRole.VIEWER, NOW, 8, 32, 0).result());
		assertEquals(FarmSharing.InviteResult.DUPLICATE,
				FarmSharing.invite(areas, farm, OWNER, PLAYER, FarmRole.OPERATOR, NOW, 8, 32, 1).result());
	}

	@Test
	void enforcesDefaultCollaboratorAndIncomingInviteLimits() {
		assertEquals(8, DreamTableConfig.maxCollaboratorsPerFarm());
		assertEquals(32, DreamTableConfig.maxPendingInvitesPerPlayer());
		assertTrue(DreamTableConfig.farmSharingEnabled());

		FarmAreas areas = farm();
		FarmAreas.Finished saved = areas.finishedBy(OWNER).getFirst();
		for (int index = 0; index < 8; index++) {
			UUID collaborator = new UUID(0L, 100L + index);
			areas.putGrant(saved.instanceId(), new FarmGrant(collaborator, FarmRole.VIEWER, NOW));
		}

		assertEquals(FarmSharing.InviteResult.COLLABORATOR_LIMIT,
				FarmSharing.invite(areas, saved.instanceId(), OWNER, PLAYER, FarmRole.VIEWER, NOW, 8, 32, 0).result());

		FarmAreas pendingAreas = farm();
		UUID pendingFarm = pendingAreas.finishedBy(OWNER).getFirst().instanceId();
		assertEquals(FarmSharing.InviteResult.PENDING_LIMIT,
				FarmSharing.invite(pendingAreas, pendingFarm, OWNER, PLAYER, FarmRole.VIEWER, NOW, 8, 32, 32).result());
	}

	@Test
	void incomingInviteCountSpansFarmStatesAndIgnoresExpiredEntries() {
		FarmAreas first = farm();
		FarmAreas second = farm();
		UUID firstFarm = first.finishedBy(OWNER).getFirst().instanceId();
		UUID secondFarm = second.finishedBy(OWNER).getFirst().instanceId();
		FarmSharing.invite(first, firstFarm, OWNER, PLAYER, FarmRole.VIEWER, NOW, 8, 32, 0);
		FarmSharing.invite(second, secondFarm, OWNER, PLAYER, FarmRole.OPERATOR, NOW, 8, 32, 1);

		assertEquals(2, FarmSharing.pendingInvites(List.of(first, second), PLAYER, NOW + 1));
		assertEquals(0, FarmSharing.pendingInvites(List.of(first, second), PLAYER,
				NOW + FarmInvite.LIFETIME_MILLIS));
	}

	@Test
	void acceptingUsesTheRoleStoredInTheInvite() {
		FarmAreas areas = farm();
		UUID farm = areas.finishedBy(OWNER).getFirst().instanceId();
		FarmSharing.InviteOutcome outcome = FarmSharing.invite(areas, farm, OWNER, PLAYER, FarmRole.OPERATOR, NOW, 8, 32, 0);

		assertEquals(FarmSharing.AcceptResult.SUCCESS,
				FarmSharing.accept(areas, farm, PLAYER, outcome.invite().inviteId(), NOW + 10, 8));
		FarmGrant grant = areas.finishedBy(OWNER).getFirst().grants().get(PLAYER);
		assertEquals(FarmRole.OPERATOR, grant.role());
		assertEquals(NOW + 10, grant.acceptedAt());
	}

	@Test
	void validatedCustomNameSurvivesARoundTripAndEmptyInputResetsIt() {
		FarmAreas areas = farm();
		UUID farm = areas.finishedBy(OWNER).getFirst().instanceId();

		assertEquals(FarmSharing.RenameResult.SUCCESS, FarmSharing.rename(areas, farm, OWNER, "  My Cane  "));
		FarmAreas restored = FarmAreas.read(areas.writeNbt(new NbtCompound(), null), null);
		assertEquals("My Cane", restored.finishedBy(OWNER).getFirst().customName());

		assertEquals(FarmSharing.RenameResult.SUCCESS, FarmSharing.rename(restored, farm, OWNER, "  "));
		assertNull(restored.finishedBy(OWNER).getFirst().customName());
	}

	@Test
	void lowerLimitsRejectNewChangesWithoutDeletingSavedData() {
		FarmAreas areas = farm();
		UUID farm = areas.finishedBy(OWNER).getFirst().instanceId();
		UUID first = new UUID(0L, 10L);
		UUID second = new UUID(0L, 11L);
		areas.putGrant(farm, new FarmGrant(first, FarmRole.VIEWER, NOW));
		areas.putGrant(farm, new FarmGrant(second, FarmRole.OPERATOR, NOW));
		FarmSharing.InviteOutcome pending = FarmSharing.invite(areas, farm, OWNER, PLAYER, FarmRole.VIEWER, NOW, 8, 32, 0);

		assertEquals(FarmSharing.AcceptResult.COLLABORATOR_LIMIT,
				FarmSharing.accept(areas, farm, PLAYER, pending.invite().inviteId(), NOW + 1, 1));
		FarmAreas restored = FarmAreas.read(areas.writeNbt(new NbtCompound(), null), null);
		FarmAreas.Finished saved = restored.finishedBy(OWNER).getFirst();
		assertEquals(2, saved.grants().size());
		assertEquals(1, saved.invites().size());
		assertNull(saved.grants().get(PLAYER));
	}

	@Test
	void expiredCleanupRemovesInvitesAndMarksTheFarmStateDirty() {
		FarmAreas areas = farm();
		UUID farm = areas.finishedBy(OWNER).getFirst().instanceId();
		FarmSharing.invite(areas, farm, OWNER, PLAYER, FarmRole.VIEWER, NOW, 8, 32, 0);
		areas.setDirty(false);

		assertEquals(1, FarmSharing.cleanupExpired(areas, NOW + Duration.ofDays(7).toMillis()));
		assertTrue(areas.isDirty());
		assertTrue(areas.finishedBy(OWNER).getFirst().invites().isEmpty());
	}

	@Test
	void anExpiredInviteDoesNotBlockAReplacementBeforePeriodicCleanup() {
		FarmAreas areas = farm();
		UUID farm = areas.finishedBy(OWNER).getFirst().instanceId();
		assertEquals(FarmSharing.InviteResult.SUCCESS,
				FarmSharing.invite(areas, farm, OWNER, PLAYER, FarmRole.VIEWER, NOW, 8, 32, 0).result());

		FarmSharing.InviteOutcome replacement = FarmSharing.invite(areas, farm, OWNER, PLAYER, FarmRole.OPERATOR,
				NOW + FarmInvite.LIFETIME_MILLIS, 8, 32, 0);

		assertEquals(FarmSharing.InviteResult.SUCCESS, replacement.result());
		assertEquals(FarmRole.OPERATOR, replacement.invite().role());
		assertEquals(1, areas.finishedBy(OWNER).getFirst().invites().size());
	}

	@Test
	void hostileSaveCollectionsAreBoundedOnRead() {
		FarmAreas areas = farm();
		NbtCompound nbt = areas.writeNbt(new NbtCompound(), null);
		NbtCompound saved = nbt.getList("Areas", 10).getCompound(0);
		NbtList grants = new NbtList();
		NbtList invites = new NbtList();
		for (int index = 0; index < 1500; index++) {
			UUID player = new UUID(10L, index + 1L);
			NbtCompound grant = new NbtCompound();
			grant.putUuid("Player", player);
			grant.putString("Role", FarmRole.VIEWER.name());
			grant.putLong("AcceptedAt", NOW);
			grants.add(grant);
			invites.add(new FarmInvite(new UUID(20L, index + 1L), player, FarmRole.OPERATOR, NOW,
					NOW + FarmInvite.LIFETIME_MILLIS).write());
		}
		saved.put("Grants", grants);
		saved.put("Invites", invites);

		FarmAreas.Finished restored = FarmAreas.read(nbt, null).finishedBy(OWNER).getFirst();

		assertTrue(restored.grants().size() <= 1024);
		assertTrue(restored.invites().size() <= 1024);
	}

	@Test
	void mutationLimitPreventsInvitesFromExceedingPersistentCapacity() {
		FarmAreas areas = farm();
		UUID farm = areas.finishedBy(OWNER).getFirst().instanceId();
		for (int index = 0; index < FarmInvite.MAX_SAVED_INVITES; index++) {
			UUID player = new UUID(30L, index + 1L);
			FarmInvite invite = FarmInvite.create(player, FarmRole.VIEWER, NOW);
			areas.putInvite(farm, invite);
		}

		FarmSharing.InviteOutcome overflow = FarmSharing.invite(areas, farm, OWNER, PLAYER, FarmRole.VIEWER,
				NOW, 8, Integer.MAX_VALUE, 0);

		assertEquals(FarmSharing.InviteResult.PENDING_LIMIT, overflow.result());
		assertEquals(FarmInvite.MAX_SAVED_INVITES, areas.finishedBy(OWNER).getFirst().invites().size());
		FarmAreas restored = FarmAreas.read(areas.writeNbt(new NbtCompound(), null), null);
		assertEquals(FarmInvite.MAX_SAVED_INVITES, restored.finishedBy(OWNER).getFirst().invites().size());
	}
}
