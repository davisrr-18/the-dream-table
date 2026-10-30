package com.davisr.thedreamtable.farm;

import com.davisr.thedreamtable.GameSetup;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A farm saved before sharing was removed: its collaborators, pending invites and payers are ignored and never written again. */
class FarmAreasLegacyRecordTest {
	private static final UUID OWNER = UUID.fromString("10000000-0000-0000-0000-000000000001");
	private static final UUID FORMER_OPERATOR = UUID.fromString("20000000-0000-0000-0000-000000000002");
	private static final UUID FORMER_VIEWER = UUID.fromString("30000000-0000-0000-0000-000000000003");
	private static final UUID INSTANCE = UUID.fromString("50000000-0000-0000-0000-000000000005");

	@BeforeAll
	static void setUp() {
		GameSetup.bootstrap();
	}

	/** The saved record of a named farm, with the collaborator, invite and payer entries that version 1.1 wrote. */
	private static NbtCompound legacySave() {
		FarmAreas areas = new FarmAreas();
		FarmSite site = new FarmSite(new BlockPos(0, 64, 0), new BlockPos(10, 64, 10), Direction.NORTH);
		areas.recordFinished(OWNER, FarmTemplates.get(FarmOptions.SUGAR_CANE.id()), site, Map.of(), INSTANCE, "My Cane");
		NbtCompound nbt = areas.writeNbt(new NbtCompound(), null);
		NbtCompound item = nbt.getList("Areas", NbtElement.COMPOUND_TYPE).getCompound(0);

		NbtList grants = new NbtList();
		NbtCompound grant = new NbtCompound();
		grant.putUuid("Player", FORMER_OPERATOR);
		grant.putString("Role", "OPERATOR");
		grant.putLong("AcceptedAt", 5L);
		grants.add(grant);
		item.put("Grants", grants);

		NbtList invites = new NbtList();
		NbtCompound invite = new NbtCompound();
		invite.putUuid("InviteId", UUID.fromString("60000000-0000-0000-0000-000000000006"));
		invite.putUuid("Player", FORMER_VIEWER);
		invite.putString("Role", "VIEWER");
		invite.putLong("CreatedAt", 1L);
		invite.putLong("ExpiresAt", 1L + Duration.ofDays(7).toMillis());
		invites.add(invite);
		item.put("Invites", invites);

		NbtList paidBy = new NbtList();
		NbtCompound payer = new NbtCompound();
		payer.putUuid("Player", FORMER_OPERATOR);
		payer.putLongArray("Steps", new long[]{1L});
		paidBy.add(payer);
		item.put("PaidBy", paidBy);
		return nbt;
	}

	@Test
	void legacyCollaboratorDataLoadsSilentlyAndTheFarmSurvives() {
		FarmAreas restored = FarmAreas.read(legacySave(), null);

		List<FarmAreas.Finished> farms = restored.finishedBy(OWNER);
		assertEquals(1, farms.size());
		assertEquals(INSTANCE, farms.getFirst().instanceId());
		assertEquals(OWNER, farms.getFirst().ownerUuid());
		assertEquals("My Cane", farms.getFirst().customName());
		assertEquals(0, restored.unreadableCount());
		assertTrue(restored.finishedBy(FORMER_OPERATOR).isEmpty());
		assertTrue(restored.finishedBy(FORMER_VIEWER).isEmpty());
	}

	@Test
	void legacyCollaboratorDataIsNotWrittenAgain() {
		NbtCompound saved = FarmAreas.read(legacySave(), null).writeNbt(new NbtCompound(), null);
		NbtCompound item = saved.getList("Areas", NbtElement.COMPOUND_TYPE).getCompound(0);

		assertFalse(item.contains("Grants"));
		assertFalse(item.contains("Invites"));
		assertFalse(item.contains("PaidBy"));
		assertEquals("My Cane", item.getString("CustomName"));
		assertEquals(OWNER, item.getUuid("Player"));
		assertEquals(INSTANCE, item.getUuid("InstanceId"));
	}
}
