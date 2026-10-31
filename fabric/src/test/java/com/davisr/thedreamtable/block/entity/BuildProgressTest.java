package com.davisr.thedreamtable.block.entity;

import com.davisr.thedreamtable.farm.BuildPace;
import com.davisr.thedreamtable.farm.FarmKey;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.UUID;

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
		progress.start(UUID.randomUUID(), true, 7L);
		progress.startRepair(farm, "River Farm");
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
		assertEquals(1, loaded.kept());
		assertEquals(progress.issue(), loaded.issue());
		loaded.start(UUID.randomUUID(), false, 7L);
		assertEquals(null, loaded.issue());
		assertTrue(loaded.repair());
		loaded.start(UUID.randomUUID(), true, 7L);
		assertFalse(loaded.repair());
		assertEquals(null, loaded.repairInstanceId());
		assertEquals(null, loaded.repairCustomName());
	}

	@Test
	void aRepairSavedWithCollaboratorsKeepsItsNameAndDropsTheCollaboratorsOnNextSave() {
		UUID farm = UUID.randomUUID();
		NbtCompound nbt = new NbtCompound();
		nbt.putBoolean("Repair", true);
		nbt.putUuid("RepairInstanceId", farm);
		nbt.putString("RepairCustomName", "River Farm");
		NbtList grants = new NbtList();
		NbtCompound grant = new NbtCompound();
		grant.putUuid("Player", UUID.randomUUID());
		grant.putString("Role", "OPERATOR");
		grant.putLong("AcceptedAt", 123L);
		grants.add(grant);
		nbt.put("RepairGrants", grants);
		NbtList invites = new NbtList();
		NbtCompound invite = new NbtCompound();
		invite.putUuid("InviteId", UUID.randomUUID());
		invite.putUuid("Player", UUID.randomUUID());
		invite.putString("Role", "VIEWER");
		invites.add(invite);
		nbt.put("RepairInvites", invites);

		BuildProgress loaded = new BuildProgress();
		loaded.read(nbt);
		NbtCompound saved = new NbtCompound();
		loaded.write(saved);

		assertTrue(loaded.repair());
		assertEquals(farm, loaded.repairInstanceId());
		assertEquals("River Farm", loaded.repairCustomName());
		assertFalse(saved.contains("RepairGrants"));
		assertFalse(saved.contains("RepairInvites"));
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

	@Test
	void finishedRecordedFlagSurvivesSaveAndLoad() {
		BuildProgress progress = new BuildProgress();
		progress.start(UUID.randomUUID(), true, 7L);
		progress.stop(DreamTableBlockEntity.PHASE_DONE);
		progress.setFinishedRecorded(false);
		NbtCompound nbt = new NbtCompound();
		progress.write(nbt);
		BuildProgress loaded = new BuildProgress();
		loaded.read(nbt);
		assertFalse(loaded.finishedRecorded());

		progress.setFinishedRecorded(true);
		nbt = new NbtCompound();
		progress.write(nbt);
		loaded.read(nbt);
		assertTrue(loaded.finishedRecorded());
	}

	@Test
	void doneSessionSavedWithoutTheFlagCountsAsRecorded() {
		NbtCompound nbt = new NbtCompound();
		nbt.putString("Phase", DreamTableBlockEntity.PHASE_DONE);
		BuildProgress loaded = new BuildProgress();
		loaded.read(nbt);
		assertTrue(loaded.finishedRecorded());

		nbt.putString("Phase", DreamTableBlockEntity.PHASE_BUILDING);
		loaded.read(nbt);
		assertFalse(loaded.finishedRecorded());
	}

	@Test
	void newBuildAndResetClearTheRecordedFlag() {
		BuildProgress progress = new BuildProgress();
		progress.setFinishedRecorded(true);
		progress.start(UUID.randomUUID(), true, 7L);
		assertFalse(progress.finishedRecorded());
		progress.setFinishedRecorded(true);
		progress.reset(false);
		assertFalse(progress.finishedRecorded());
	}

	@Test
	void aCorruptStepNumberIsClampedInsteadOfSizingTheLegacyStepSet() {
		assertEquals(0, BuildProgress.clampIndex(-5));
		assertEquals(40, BuildProgress.clampIndex(40));
		assertEquals(com.davisr.thedreamtable.farm.schematic.SchematicReader.MAX_BLOCKS, BuildProgress.clampIndex(Integer.MAX_VALUE));

		NbtCompound nbt = new NbtCompound();
		nbt.putString("Phase", DreamTableBlockEntity.PHASE_DONE);
		nbt.putInt("BuildIndex", Integer.MAX_VALUE);
		BuildProgress loaded = new BuildProgress();
		loaded.read(nbt);

		assertEquals(com.davisr.thedreamtable.farm.schematic.SchematicReader.MAX_BLOCKS, loaded.index());
		assertTrue(loaded.placedSteps().length() <= com.davisr.thedreamtable.farm.schematic.SchematicReader.MAX_BLOCKS);
	}

	@Test
	void theNoticeOfARepairEndedWhileAwaySurvivesSavingAndIsClearedByAnyNewStart() {
		BuildProgress progress = new BuildProgress();
		progress.setReturnNotice("gui.the_dream_table.repair_ended_revoked");
		NbtCompound nbt = new NbtCompound();
		progress.write(nbt);

		BuildProgress loaded = new BuildProgress();
		loaded.read(nbt);
		assertEquals("gui.the_dream_table.repair_ended_revoked", loaded.returnNotice());

		loaded.start(UUID.randomUUID(), true, 7L);
		assertEquals("", loaded.returnNotice());
		loaded.setReturnNotice("x");
		loaded.reset(false);
		assertEquals("", loaded.returnNotice());
		assertEquals("", new BuildProgress().returnNotice());
	}

	@Test
	void aBuildFromScratchStartsWithNoLayerClearedAndRemembersEachLayerItClears() {
		BuildProgress progress = new BuildProgress();
		progress.start(UUID.randomUUID(), true, 7L);
		assertFalse(progress.layerCleared(0));
		progress.markLayerCleared(0);
		assertTrue(progress.layerCleared(0));
		assertFalse(progress.layerCleared(1));
	}

	@Test
	void aSaveWithoutTheClearedLayersKeyCountsEveryLayerAsClearedAndStaysSo() {
		NbtCompound nbt = new NbtCompound();
		nbt.putString("Phase", DreamTableBlockEntity.PHASE_PAUSED);
		nbt.putInt("BuildIndex", 5);
		BuildProgress loaded = new BuildProgress();
		loaded.read(nbt);
		assertTrue(loaded.layerCleared(0));
		assertTrue(loaded.layerCleared(40));

		loaded.start(UUID.randomUUID(), false, 7L);
		loaded.markLayerCleared(0);
		assertTrue(loaded.layerCleared(40));
		NbtCompound saved = new NbtCompound();
		loaded.write(saved);
		assertFalse(saved.contains("ClearedLayers"));
	}

	@Test
	void clearedLayersSurviveASaveAndAResumeButAFreshStartForgetsThem() {
		UUID owner = UUID.randomUUID();
		BuildProgress progress = new BuildProgress();
		progress.start(owner, true, 7L);
		progress.markLayerCleared(0);
		progress.markLayerCleared(3);
		progress.stop(DreamTableBlockEntity.PHASE_PAUSED);
		NbtCompound nbt = new NbtCompound();
		progress.write(nbt);
		assertTrue(nbt.contains("ClearedLayers", NbtElement.LONG_ARRAY_TYPE));

		BuildProgress loaded = new BuildProgress();
		loaded.read(nbt);
		assertTrue(loaded.layerCleared(0));
		assertFalse(loaded.layerCleared(1));
		assertTrue(loaded.layerCleared(3));

		loaded.start(owner, false, 7L);
		assertTrue(loaded.layerCleared(3));
		assertFalse(loaded.layerCleared(1));

		loaded.start(owner, true, 7L);
		assertFalse(loaded.layerCleared(0));
		assertFalse(loaded.layerCleared(3));
	}

	@Test
	void aFreshBuildSavesTheKeyEvenBeforeAnyLayerIsCleared() {
		BuildProgress progress = new BuildProgress();
		progress.start(UUID.randomUUID(), true, 7L);
		NbtCompound nbt = new NbtCompound();
		progress.write(nbt);
		assertTrue(nbt.contains("ClearedLayers", NbtElement.LONG_ARRAY_TYPE));

		BuildProgress loaded = new BuildProgress();
		loaded.read(nbt);
		assertFalse(loaded.layerCleared(0));
	}

	@Test
	void resetForgetsTheClearedLayersSoAnIdleSessionSavesNone() {
		BuildProgress progress = new BuildProgress();
		progress.start(UUID.randomUUID(), true, 7L);
		progress.markLayerCleared(2);
		progress.reset(true);
		NbtCompound nbt = new NbtCompound();
		progress.write(nbt);
		assertFalse(nbt.contains("ClearedLayers"));
	}
}
