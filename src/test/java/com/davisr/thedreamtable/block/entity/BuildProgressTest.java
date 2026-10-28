package com.davisr.thedreamtable.block.entity;

import com.davisr.thedreamtable.farm.BuildPace;
import com.davisr.thedreamtable.farm.FarmGrant;
import com.davisr.thedreamtable.farm.FarmInvite;
import com.davisr.thedreamtable.farm.FarmKey;
import com.davisr.thedreamtable.farm.FarmRole;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildProgressTest {
	private static int placedOver(BuildProgress progress, int rate, int ticks) {
		int total = 0;
		for (int tick = 0; tick < ticks; tick++) {
			total += progress.due(rate);
		}
		return total;
	}

	@Test
	void firstBlockComesRightAwayThenThePaceHolds() {
		BuildProgress progress = new BuildProgress();
		progress.start(UUID.randomUUID(), true, 7L);
		assertEquals(1, progress.due(1));
		assertEquals(1, placedOver(progress, 1, 20));
	}

	@Test
	void fastPacesAreSpreadOverEveryTick() {
		BuildProgress progress = new BuildProgress();
		progress.reset(true);
		for (int tick = 0; tick < 20; tick++) {
			assertEquals(2, progress.due(40));
		}
		assertEquals(30, placedOver(progress, 30, 20));
		assertTrue(Math.abs(64 * 3 - placedOver(progress, 64, 60)) <= 1);
	}

	@Test
	void startingFromScratchForgetsPlacedSteps() {
		BuildProgress progress = new BuildProgress();
		progress.start(UUID.randomUUID(), true, 7L);
		progress.recordPlaced(true);
		progress.advance();
		assertTrue(progress.placed(0));
		assertTrue(progress.paid(0));
		progress.start(UUID.randomUUID(), true, 7L);
		assertFalse(progress.placed(0));
		assertEquals(0, progress.index());
	}

	@Test
	void resumingKeepsPlacedSteps() {
		BuildProgress progress = new BuildProgress();
		progress.start(UUID.randomUUID(), true, 7L);
		progress.recordPlaced(false);
		progress.advance();
		progress.stop(DreamTableBlockEntity.PHASE_PAUSED);
		assertTrue(progress.resumable());
		progress.start(UUID.randomUUID(), false, 7L);
		assertTrue(progress.placed(0));
		assertFalse(progress.paid(0));
		assertEquals(1, progress.index());
	}

	@Test
	void signatureDetectsAChangedFarm() {
		BuildProgress progress = new BuildProgress();
		assertTrue(progress.matches(123L));
		progress.start(UUID.randomUUID(), true, 7L);
		assertTrue(progress.matches(7L));
		assertFalse(progress.matches(8L));
	}

	@Test
	void saveAndLoadKeepsEverything() {
		BuildProgress progress = new BuildProgress();
		UUID owner = UUID.randomUUID();
		progress.setPace(BuildPace.SLOW);
		progress.start(owner, true, 42L);
		progress.recordPlaced(true);
		progress.advance();
		progress.advance();
		progress.stop(DreamTableBlockEntity.PHASE_NEED);
		NbtCompound nbt = new NbtCompound();
		progress.write(nbt);

		BuildProgress loaded = new BuildProgress();
		loaded.read(nbt);
		assertEquals(DreamTableBlockEntity.PHASE_NEED, loaded.phase());
		assertEquals(2, loaded.index());
		assertEquals(BuildPace.SLOW, loaded.pace());
		assertEquals(owner, loaded.owner());
		assertTrue(loaded.placed(0));
		assertTrue(loaded.paid(0));
		assertTrue(loaded.matches(42L));
		assertFalse(loaded.matches(43L));
	}

	@Test
	void oldSpeedsLoadAsTheDefaultLevel() {
		NbtCompound nbt = new NbtCompound();
		nbt.putInt("BlocksPerSecond", 4);
		nbt.putInt("Ticks", 11);
		BuildProgress loaded = new BuildProgress();
		loaded.read(nbt);
		assertEquals(BuildPace.DEFAULT, loaded.pace());
		assertEquals(DreamTableBlockEntity.PHASE_IDLE, loaded.phase());
	}

	@Test
	void aRepairAndWhyItStoppedAreSavedAndClearedOnRestart() {
		BuildProgress progress = new BuildProgress();
		UUID farm = UUID.randomUUID();
		UUID operator = UUID.randomUUID();
		Map<UUID, FarmGrant> grants = Map.of(operator, new FarmGrant(operator, FarmRole.OPERATOR, 123L));
		UUID viewer = UUID.randomUUID();
		FarmInvite invite = FarmInvite.create(viewer, FarmRole.VIEWER, 456L);
		Map<UUID, FarmInvite> invites = Map.of(invite.inviteId(), invite);
		progress.start(UUID.randomUUID(), true, 7L);
		progress.startRepair(farm, "River Farm", grants, invites);
		progress.keep();
		progress.setIssue(BuildIssue.at(BuildIssue.Kind.PROTECTED, new BlockPos(1, 2, 3)));
		progress.stop(DreamTableBlockEntity.PHASE_BLOCKED);
		NbtCompound nbt = new NbtCompound();
		progress.write(nbt);
		BuildProgress loaded = new BuildProgress();
		loaded.read(nbt);
		assertTrue(loaded.repair());
		assertEquals(farm, loaded.repairInstanceId());
		assertEquals("River Farm", loaded.repairCustomName());
		assertEquals(grants, loaded.repairGrants());
		assertEquals(invites, loaded.repairInvites());
		assertEquals(1, loaded.kept());
		assertEquals(progress.issue(), loaded.issue());
		loaded.start(UUID.randomUUID(), false, 7L);
		assertEquals(null, loaded.issue());
		assertTrue(loaded.repair());
		loaded.start(UUID.randomUUID(), true, 7L);
		assertFalse(loaded.repair());
		assertEquals(null, loaded.repairInstanceId());
		assertEquals(null, loaded.repairCustomName());
		assertTrue(loaded.repairGrants().isEmpty());
		assertTrue(loaded.repairInvites().isEmpty());
	}

	@Test
	void aBuildReportsDroppedHandoversOnlyOnce() {
		BuildProgress progress = new BuildProgress();
		progress.start(UUID.randomUUID(), true, 7L);

		assertFalse(progress.shouldNotifyDropped(false));
		assertTrue(progress.shouldNotifyDropped(true));
		assertFalse(progress.shouldNotifyDropped(true));

		progress.start(UUID.randomUUID(), true, 7L);
		assertTrue(progress.shouldNotifyDropped(true));
	}

	@Test
	void aLegacyRepairWithoutPublicIdentityGetsOneWhenLoaded() {
		NbtCompound nbt = new NbtCompound();
		nbt.putBoolean("Repair", true);

		BuildProgress loaded = new BuildProgress();
		loaded.read(nbt);

		assertNotNull(loaded.repairInstanceId());
		assertTrue(loaded.repairGrants().isEmpty());
	}

	@Test
	void aNormalBuildIgnoresStrayRepairMetadata() {
		NbtCompound nbt = new NbtCompound();
		nbt.putUuid("RepairInstanceId", UUID.randomUUID());
		nbt.putString("RepairCustomName", "Injected");

		BuildProgress loaded = new BuildProgress();
		loaded.read(nbt);

		assertFalse(loaded.repair());
		assertEquals(null, loaded.repairInstanceId());
		assertEquals(null, loaded.repairCustomName());
		assertTrue(loaded.repairGrants().isEmpty());
		assertTrue(loaded.repairInvites().isEmpty());
	}

	@Test
	void repairContextKeepsOwnerActorAndOperationSeparateAcrossRestart() {
		UUID owner = UUID.fromString("10000000-0000-0000-0000-000000000001");
		UUID actor = UUID.fromString("20000000-0000-0000-0000-000000000002");
		UUID operation = UUID.fromString("30000000-0000-0000-0000-000000000003");
		FarmKey key = new FarmKey(Identifier.of("minecraft", "overworld"), UUID.randomUUID());
		RepairContext context = new RepairContext(key, owner, actor, operation);
		BuildProgress progress = new BuildProgress();
		progress.start(actor, true, 42L);
		progress.startRepair(context);

		NbtCompound nbt = new NbtCompound();
		progress.write(nbt);
		BuildProgress restored = new BuildProgress();
		restored.read(nbt);

		assertEquals(actor, restored.actorUuid());
		assertEquals(context, restored.repairContext());
		assertEquals(key.instanceId(), restored.repairInstanceId());
	}

	@Test
	void legacyOwnerFieldStillLoadsAsTheRepairActor() {
		UUID actor = UUID.randomUUID();
		NbtCompound nbt = new NbtCompound();
		nbt.putBoolean("Repair", true);
		nbt.putUuid("Owner", actor);

		BuildProgress restored = new BuildProgress();
		restored.read(nbt);

		assertEquals(actor, restored.actorUuid());
		assertEquals(null, restored.repairContext());
	}
}
