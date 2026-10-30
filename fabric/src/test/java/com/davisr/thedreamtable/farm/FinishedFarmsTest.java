package com.davisr.thedreamtable.farm;

import com.davisr.thedreamtable.GameSetup;
import com.davisr.thedreamtable.farm.alert.FarmAlertType;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A player's finished farms survive saving, loading, and updates that change the farm they were built from. */
class FinishedFarmsTest {
	private static final UUID PLAYER = UUID.nameUUIDFromBytes("OfflinePlayer:Davi".getBytes());
	private static final FarmSite SITE = new FarmSite(new BlockPos(0, 64, 0), new BlockPos(10, 64, 10), Direction.NORTH);

	@BeforeAll
	static void setUp() {
		GameSetup.bootstrap();
	}

	private static NbtCompound saved(FarmTemplate template) {
		FarmAreas areas = new FarmAreas();
		areas.recordFinished(PLAYER, template, SITE, Map.of());
		return areas.writeNbt(new NbtCompound(), null);
	}

	@Test
	void aFinishedFarmKeepsItsDesignThroughASave() {
		FarmTemplate cane = FarmTemplates.get(FarmOptions.SUGAR_CANE.id());
		List<FarmAreas.Finished> farms = FarmAreas.read(saved(cane), null).finishedBy(PLAYER);
		assertEquals(1, farms.size());
		FarmAreas.Finished farm = farms.get(0);
		assertEquals(FarmOptions.SUGAR_CANE.id(), farm.farmId());
		assertNotNull(farm.layout());
		assertEquals(cane.signature(), farm.layout().signature());
		assertEquals(cane.steps().size(), farm.layout().steps().size());
		assertEquals(cane.steps(), farm.layout().steps());
		assertEquals(cane.signature(), FarmTemplates.get(farm.layout().id()).signature());
		assertEquals(PLAYER, farm.ownerUuid());
	}

	@Test
	void instanceIdentitySurvivesARoundTrip() {
		FarmAreas areas = new FarmAreas();
		areas.recordFinished(PLAYER, FarmTemplates.get(FarmOptions.SUGAR_CANE.id()), SITE, Map.of());
		UUID before = areas.finishedBy(PLAYER).getFirst().instanceId();

		FarmAreas restored = FarmAreas.read(areas.writeNbt(new NbtCompound(), null), null);

		assertEquals(before, restored.finishedBy(PLAYER).getFirst().instanceId());
	}

	@Test
	void repairCanRestoreTheSameIdentityAndCollaborators() {
		FarmTemplate template = FarmTemplates.get(FarmOptions.SUGAR_CANE.id());
		UUID instanceId = UUID.randomUUID();
		UUID operator = UUID.randomUUID();
		Map<UUID, FarmGrant> grants = Map.of(operator, new FarmGrant(operator, FarmRole.OPERATOR, 123L));
		FarmAreas areas = new FarmAreas();

		areas.recordFinished(PLAYER, template, SITE, Map.of(), instanceId, grants);
		FarmAreas restored = FarmAreas.read(areas.writeNbt(new NbtCompound(), null), null);
		FarmAreas.Finished farm = restored.finishedBy(PLAYER).getFirst();

		assertEquals(instanceId, farm.instanceId());
		assertEquals(grants, farm.grants());
		FarmRecord record = FarmDirectory.record(new FarmKey(net.minecraft.util.Identifier.of("minecraft", "overworld"), instanceId), farm);
		assertTrue(FarmAccess.allows(record, operator, FarmAction.REPAIR, true));
	}

	@Test
	void legacyFarmGetsAnInstanceIdentityAndMarksTheMigrationDirty() {
		NbtCompound nbt = saved(FarmTemplates.get(FarmOptions.BAMBOO.id()));
		NbtCompound area = nbt.getList("Areas", 10).getCompound(0);
		area.remove("InstanceId");

		FarmAreas restored = FarmAreas.read(nbt, null);

		assertNotNull(restored.finishedBy(PLAYER).getFirst().instanceId());
		assertTrue(restored.isDirty());
		NbtCompound migrated = restored.writeNbt(new NbtCompound(), null).getList("Areas", 10).getCompound(0);
		assertTrue(migrated.containsUuid("InstanceId"));
		assertEquals(area.getLong("Table"), migrated.getLong("Table"));
		assertEquals(area.getLong("Spot"), migrated.getLong("Spot"));
		assertEquals(area.getUuid("Player"), migrated.getUuid("Player"));
		assertEquals(area.getString("Farm"), migrated.getString("Farm"));
	}

	@Test
	void unsavedLegacyMigrationProducesTheSameIdentityOnTheNextLoad() {
		NbtCompound nbt = saved(FarmTemplates.get(FarmOptions.BAMBOO.id()));
		nbt.getList("Areas", 10).getCompound(0).remove("InstanceId");

		UUID first = FarmAreas.read(nbt, null).finishedBy(PLAYER).getFirst().instanceId();
		UUID second = FarmAreas.read(nbt, null).finishedBy(PLAYER).getFirst().instanceId();

		assertEquals(first, second);
	}

	@Test
	void oneCorruptFinishedRecordIsSkippedWithoutLosingTheOthers() {
		FarmTemplate template = FarmTemplates.get(FarmOptions.CROP.id());
		FarmAreas areas = new FarmAreas();
		FarmSite otherSite = new FarmSite(SITE.table(), new BlockPos(30, 64, 30), Direction.NORTH);
		areas.recordFinished(PLAYER, template, SITE, Map.of());
		areas.recordFinished(PLAYER, template, otherSite, Map.of());
		NbtCompound nbt = areas.writeNbt(new NbtCompound(), null);
		NbtCompound layout = nbt.getList("Areas", 10).getCompound(0).getCompound("Layout");
		layout.putIntArray("State", new int[0]);

		FarmAreas restored = FarmAreas.read(nbt, null);

		assertEquals(1, restored.finishedBy(PLAYER).size());
		assertTrue(restored.isDirty());
	}

	@Test
	void duplicateManipulatedInstanceIdentityIsRepairedWithoutDroppingEitherFarm() {
		FarmTemplate template = FarmTemplates.get(FarmOptions.CROP.id());
		FarmAreas areas = new FarmAreas();
		FarmSite otherSite = new FarmSite(SITE.table(), new BlockPos(30, 64, 30), Direction.NORTH);
		areas.recordFinished(PLAYER, template, SITE, Map.of());
		areas.recordFinished(PLAYER, template, otherSite, Map.of());
		NbtCompound nbt = areas.writeNbt(new NbtCompound(), null);
		NbtList list = nbt.getList("Areas", 10);
		list.getCompound(1).putUuid("InstanceId", list.getCompound(0).getUuid("InstanceId"));

		FarmAreas restored = FarmAreas.read(nbt, null);
		List<FarmAreas.Finished> farms = restored.finishedBy(PLAYER);

		assertEquals(2, farms.size());
		assertNotEquals(farms.get(0).instanceId(), farms.get(1).instanceId());
		assertTrue(restored.isDirty());
	}

	@Test
	void rebuildingAtTheSameSiteNeverInheritsTheRemovedFarmsIdentity() {
		FarmTemplate template = FarmTemplates.get(FarmOptions.SUGAR_CANE.id());
		FarmAreas areas = new FarmAreas();
		areas.recordFinished(PLAYER, template, SITE, Map.of());
		FarmAreas.Finished first = areas.finishedBy(PLAYER).getFirst();
		areas.release(first.key());

		areas.recordFinished(PLAYER, template, SITE, Map.of());

		assertNotEquals(first.instanceId(), areas.finishedBy(PLAYER).getFirst().instanceId());
	}

	@Test
	void aFarmIsFoundByItsInstanceIdAcrossTheChangesToItsRecords() {
		FarmTemplate cane = FarmTemplates.get(FarmOptions.SUGAR_CANE.id());
		FarmSite otherSite = new FarmSite(new BlockPos(0, 64, 0), new BlockPos(60, 64, 60), Direction.NORTH);
		FarmAreas areas = new FarmAreas();
		assertTrue(areas.finished(UUID.randomUUID()).isEmpty());
		areas.recordFinished(PLAYER, cane, SITE, Map.of());
		UUID first = areas.finishedBy(PLAYER).getFirst().instanceId();
		assertEquals(first, areas.finished(first).orElseThrow().instanceId());
		areas.recordFinished(PLAYER, cane, otherSite, Map.of());
		UUID second = areas.finishedBy(PLAYER).stream().map(FarmAreas.Finished::instanceId).filter(id -> !id.equals(first)).findFirst().orElseThrow();
		assertEquals(second, areas.finished(second).orElseThrow().instanceId());
		assertTrue(areas.hasFinished(first));

		areas.setCustomName(second, "Second");
		assertEquals("Second", areas.finished(second).orElseThrow().customName());
		assertNull(areas.finished(first).orElseThrow().customName());

		areas.release(areas.finished(first).orElseThrow().key());
		assertTrue(areas.finished(first).isEmpty());
		assertFalse(areas.hasFinished(first));
		assertTrue(areas.alerts(first).isEmpty());
		assertEquals(second, areas.finished(second).orElseThrow().instanceId());

		FarmAreas restored = FarmAreas.read(areas.writeNbt(new NbtCompound(), null), null);
		assertEquals("Second", restored.finished(second).orElseThrow().customName());
		assertTrue(restored.finished(first).isEmpty());
	}

	@Test
	void savingTwiceWritesTheSameLayoutEvenIfTheFirstSaveWasChanged() {
		FarmTemplate cane = FarmTemplates.get(FarmOptions.SUGAR_CANE.id());
		FarmAreas areas = new FarmAreas();
		areas.recordFinished(PLAYER, cane, SITE, Map.of());
		NbtCompound first = areas.writeNbt(new NbtCompound(), null);
		first.getList("Areas", NbtElement.COMPOUND_TYPE).getCompound(0).getCompound("Layout").putIntArray("State", new int[0]);
		NbtCompound second = areas.writeNbt(new NbtCompound(), null);
		assertEquals(cane.steps(), FarmAreas.read(second, null).finishedBy(PLAYER).getFirst().layout().steps());
	}

	@Test
	void finishedFarmKeepsItsAlertStateThroughASave() {
		FarmAreas areas = new FarmAreas();
		areas.recordFinished(PLAYER, FarmTemplates.get(FarmOptions.SUGAR_CANE.id()), SITE, Map.of());
		UUID instanceId = areas.finishedBy(PLAYER).getFirst().instanceId();
		areas.observeAlert(instanceId, FarmAlertType.BLOCKS_MISSING, 4, 120L);

		FarmAreas restored = FarmAreas.read(areas.writeNbt(new NbtCompound(), null), null);

		assertEquals(4, restored.alerts(instanceId).orElseThrow().active(FarmAlertType.BLOCKS_MISSING).orElseThrow().quantity());
	}

	@Test
	void repairingFarmIsNotForgottenByAConcurrentConflictWhenItsPresenceIsLow() {
		FarmTemplate template = FarmTemplates.get(FarmOptions.SUGAR_CANE.id());
		UUID instanceId = UUID.randomUUID();
		UUID operator = UUID.randomUUID();
		Map<UUID, FarmGrant> grants = Map.of(operator, new FarmGrant(operator, FarmRole.OPERATOR, 1_700_000_000_000L));
		FarmAreas areas = new FarmAreas();
		areas.recordFinished(PLAYER, template, SITE, Map.of(), instanceId, grants);
		areas.setCustomName(instanceId, "River Works");
		NbtCompound saved = areas.writeNbt(new NbtCompound(), null);
		NbtCompound item = saved.getList("Areas", NbtElement.COMPOUND_TYPE).getCompound(0);
		item.remove("Layout");
		item.putLong("FarmSignature", Long.MIN_VALUE);
		FarmAreas restored = FarmAreas.read(saved, null);
		FarmAreas.Finished before = restored.finished(instanceId).orElseThrow();
		UUID operationId = UUID.randomUUID();
		restored.beginRepair(instanceId, PLAYER, SITE.table(), operationId).orElseThrow();

		FarmAreas.Key concurrentBuild = FarmAreas.Key.build(new BlockPos(200, 64, 200), UUID.randomUUID());
		assertEquals(FarmAreas.Conflict.FINISHED, restored.conflict(null, concurrentBuild, before.box()));
		assertTrue(restored.completeRepair(instanceId, operationId));
		FarmAreas.Finished after = restored.finished(instanceId).orElseThrow();
		assertEquals(before.instanceId(), after.instanceId());
		assertEquals(before.customName(), after.customName());
		assertEquals(before.grants(), after.grants());
	}

	@Test
	void observingAChangedAlertMarksFinishedFarmStateDirty() {
		FarmAreas areas = new FarmAreas();
		areas.recordFinished(PLAYER, FarmTemplates.get(FarmOptions.SUGAR_CANE.id()), SITE, Map.of());
		UUID instanceId = areas.finishedBy(PLAYER).getFirst().instanceId();
		areas.setDirty(false);

		areas.observeAlert(instanceId, FarmAlertType.STORAGE_FULL, 1, 120L);

		assertTrue(areas.isDirty());
	}

	@Test
	void farmsSavedBeforeDesignsWereKeptAdoptTheMatchingDesign() {
		NbtCompound nbt = saved(FarmTemplates.get(FarmOptions.BAMBOO.id()));
		nbt.getList("Areas", 10).getCompound(0).remove("Layout");
		FarmAreas.Finished farm = FarmAreas.read(nbt, null).finishedBy(PLAYER).get(0);
		assertNotNull(farm.layout());
		assertEquals(FarmTemplates.get(FarmOptions.BAMBOO.id()).signature(), farm.layout().signature());
	}

	@Test
	void aFarmWhoseOldDesignIsGoneIsStillListed() {
		NbtCompound nbt = saved(FarmTemplates.get(FarmOptions.CROP.id()));
		NbtCompound area = nbt.getList("Areas", 10).getCompound(0);
		area.remove("Layout");
		area.putLong("FarmSignature", 12345L);
		List<FarmAreas.Finished> farms = FarmAreas.read(nbt, null).finishedBy(PLAYER);
		assertEquals(1, farms.size());
		assertNull(farms.get(0).layout());
	}

	@Test
	void otherPlayersDontSeeTheFarm() {
		NbtCompound nbt = saved(FarmTemplates.get(FarmOptions.SUGAR_CANE.id()));
		NbtList areas = nbt.getList("Areas", 10);
		assertEquals(1, areas.size());
		assertEquals(0, FarmAreas.read(nbt, null).finishedBy(UUID.randomUUID()).size());
	}

	@Test
	void finishedFarmsWithTheSameShapeKeepTheirOwnBlockData() {
		NbtCompound firstData = new NbtCompound();
		firstData.putString("Lock", "first");
		NbtCompound secondData = new NbtCompound();
		secondData.putString("Lock", "second");
		FarmTemplate first = FarmTemplate.of("test:filtered_hopper", List.of(
				FarmStep.block(0, 0, 0, Blocks.HOPPER.getDefaultState(), Items.HOPPER, firstData)));
		FarmTemplate second = FarmTemplate.of("test:filtered_hopper", List.of(
				FarmStep.block(0, 0, 0, Blocks.HOPPER.getDefaultState(), Items.HOPPER, secondData)));
		assertEquals(first.signature(), second.signature());

		FarmAreas areas = new FarmAreas();
		FarmSite otherSite = new FarmSite(SITE.table(), new BlockPos(30, 64, 30), Direction.NORTH);
		areas.recordFinished(PLAYER, first, SITE, Map.of());
		areas.recordFinished(PLAYER, second, otherSite, Map.of());
		List<FarmAreas.Finished> farms = FarmAreas.read(areas.writeNbt(new NbtCompound(), null), null).finishedBy(PLAYER);

		assertEquals(2, farms.size());
		FarmAreas.Finished firstFarm = farms.stream().filter(farm -> farm.where().equals(SITE.anchor())).findFirst().orElseThrow();
		FarmAreas.Finished secondFarm = farms.stream().filter(farm -> farm.where().equals(otherSite.anchor())).findFirst().orElseThrow();
		assertNotEquals(firstFarm.layout().id(), secondFarm.layout().id());
		assertEquals("first", FarmTemplates.get(firstFarm.layout().id()).steps().get(0).blockNbt().getString("Lock"));
		assertEquals("second", FarmTemplates.get(secondFarm.layout().id()).steps().get(0).blockNbt().getString("Lock"));
	}

	@Test
	void placedAndPaidStepsSurviveASaveAndLegacyRecordsHaveEveryStepSet() {
		FarmTemplate cane = FarmTemplates.get(FarmOptions.SUGAR_CANE.id());
		BitSet placed = new BitSet();
		placed.set(1);
		placed.set(3);
		BitSet paid = new BitSet();
		paid.set(3);
		FarmAreas areas = new FarmAreas();
		areas.recordFinished(PLAYER, cane, SITE, Map.of(), UUID.randomUUID(), null, Map.of(), Map.of(), placed, paid);
		NbtCompound nbt = areas.writeNbt(new NbtCompound(), null);

		FarmAreas.Finished farm = FarmAreas.read(nbt, null).finishedBy(PLAYER).getFirst();
		assertEquals(placed, farm.placed());
		assertEquals(paid, farm.paid());

		NbtCompound item = nbt.getList("Areas", NbtElement.COMPOUND_TYPE).getCompound(0);
		item.remove("PlacedSteps");
		item.remove("PaidSteps");
		FarmAreas.Finished legacy = FarmAreas.read(nbt, null).finishedBy(PLAYER).getFirst();
		BitSet every = new BitSet();
		every.set(0, cane.steps().size());
		assertEquals(every, legacy.placed());
		assertEquals(every, legacy.paid());
	}

	@Test
	void aRepairAddsItsPlacementsAndTheirPaymentToTheRecord() {
		FarmTemplate cane = FarmTemplates.get(FarmOptions.SUGAR_CANE.id());
		UUID instanceId = UUID.randomUUID();
		BitSet placed = new BitSet();
		placed.set(0);
		placed.set(1);
		BitSet paid = new BitSet();
		paid.set(0);
		paid.set(1);
		FarmAreas areas = new FarmAreas();
		areas.recordFinished(PLAYER, cane, SITE, Map.of(), instanceId, null, Map.of(), Map.of(), placed, paid);
		UUID operationId = UUID.randomUUID();
		areas.beginRepair(instanceId, PLAYER, SITE.table(), operationId).orElseThrow();
		BitSet repaired = new BitSet();
		repaired.set(1);
		repaired.set(2);
		BitSet repairPaid = new BitSet();
		repairPaid.set(2);

		assertTrue(areas.completeRepair(instanceId, operationId, PLAYER, PLAYER, repaired, repairPaid));

		FarmAreas.Finished after = areas.finished(instanceId).orElseThrow();
		BitSet expectedPlaced = new BitSet();
		expectedPlaced.set(0, 3);
		BitSet expectedPaid = new BitSet();
		expectedPaid.set(0);
		expectedPaid.set(2);
		assertEquals(expectedPlaced, after.placed());
		assertEquals(expectedPaid, after.paid());
		assertFalse(areas.completeRepair(instanceId, operationId, PLAYER, PLAYER, repaired, repairPaid));
	}

	@Test
	void anUnreadableRecordIsKeptUnchangedThroughASaveSoALaterVersionCanReadIt() {
		FarmTemplate template = FarmTemplates.get(FarmOptions.CROP.id());
		FarmAreas areas = new FarmAreas();
		FarmSite otherSite = new FarmSite(SITE.table(), new BlockPos(30, 64, 30), Direction.NORTH);
		areas.recordFinished(PLAYER, template, SITE, Map.of());
		areas.recordFinished(PLAYER, template, otherSite, Map.of());
		NbtCompound nbt = areas.writeNbt(new NbtCompound(), null);
		NbtCompound broken = nbt.getList("Areas", 10).getCompound(0);
		broken.getCompound("Layout").putIntArray("State", new int[0]);
		NbtCompound original = broken.copy();

		FarmAreas restored = FarmAreas.read(nbt, null);
		assertEquals(1, restored.finishedBy(PLAYER).size());
		assertEquals(1, restored.unreadableCount());

		NbtList resaved = restored.writeNbt(new NbtCompound(), null).getList("Areas", NbtElement.COMPOUND_TYPE);
		assertEquals(2, resaved.size());
		assertTrue(resaved.stream().anyMatch(original::equals), "the unreadable record was written back as it was saved");

		// The record survives any number of load and save cycles, still untouched.
		FarmAreas again = FarmAreas.read(restored.writeNbt(new NbtCompound(), null), null);
		assertEquals(1, again.unreadableCount());
		assertTrue(again.writeNbt(new NbtCompound(), null).getList("Areas", NbtElement.COMPOUND_TYPE).stream().anyMatch(original::equals));
	}

	@Test
	void aRecordWithoutABoxOrPlayerIsAlsoKeptNotDeleted() {
		NbtCompound nbt = saved(FarmTemplates.get(FarmOptions.CROP.id()));
		NbtCompound junk = new NbtCompound();
		junk.putString("Kind", "FINISHED");
		nbt.getList("Areas", NbtElement.COMPOUND_TYPE).add(junk.copy());

		FarmAreas restored = FarmAreas.read(nbt, null);

		assertEquals(1, restored.finishedBy(PLAYER).size());
		assertEquals(1, restored.unreadableCount());
		assertTrue(restored.writeNbt(new NbtCompound(), null).getList("Areas", NbtElement.COMPOUND_TYPE).stream().anyMatch(junk::equals));
	}

	@Test
	void aRepairWhoseLeaseWasReconciledAwayStillMergesItsPlacements() {
		FarmTemplate cane = FarmTemplates.get(FarmOptions.SUGAR_CANE.id());
		UUID instanceId = UUID.randomUUID();
		BitSet placed = new BitSet();
		placed.set(0);
		BitSet paid = new BitSet();
		paid.set(0);
		FarmAreas areas = new FarmAreas();
		areas.recordFinished(PLAYER, cane, SITE, Map.of(), instanceId, null, Map.of(), Map.of(), placed, paid);
		UUID operationId = UUID.randomUUID();
		areas.beginRepair(instanceId, PLAYER, SITE.table(), operationId).orElseThrow();
		areas.failRepair(instanceId, operationId);
		BitSet repaired = new BitSet();
		repaired.set(1);
		repaired.set(2);
		BitSet repairPaid = new BitSet();
		repairPaid.set(2);

		assertFalse(areas.completeRepair(instanceId, operationId, PLAYER, PLAYER, repaired, repairPaid));
		assertEquals(placed, areas.finished(instanceId).orElseThrow().placed(), "the lease-bound path changes nothing");

		areas.mergePlacement(instanceId, PLAYER, PLAYER, repaired, repairPaid);

		BitSet expectedPlaced = new BitSet();
		expectedPlaced.set(0, 3);
		BitSet expectedPaid = new BitSet();
		expectedPaid.set(0);
		expectedPaid.set(2);
		assertEquals(expectedPlaced, areas.finished(instanceId).orElseThrow().placed());
		assertEquals(expectedPaid, areas.finished(instanceId).orElseThrow().paid());
		areas.mergePlacement(UUID.randomUUID(), PLAYER, PLAYER, repaired, repairPaid);
	}

	@Test
	void restoredAlertStateReplacesWhatReleasingTheRecordDropped() {
		FarmTemplate cane = FarmTemplates.get(FarmOptions.SUGAR_CANE.id());
		UUID instanceId = UUID.randomUUID();
		FarmAreas areas = new FarmAreas();
		areas.recordFinished(PLAYER, cane, SITE, Map.of(), instanceId, null, Map.of(), Map.of());
		areas.observeAlert(instanceId, FarmAlertType.BLOCKS_MISSING, 4, 120L);
		NbtCompound snapshot = areas.alerts(instanceId).orElseThrow().write(new NbtCompound());

		areas.release(FarmAreas.Key.finished(PLAYER, SITE));
		areas.recordFinished(PLAYER, cane, SITE, Map.of(), instanceId, null, Map.of(), Map.of());
		assertTrue(areas.alerts(instanceId).orElseThrow().active().isEmpty());
		areas.restoreAlerts(instanceId, com.davisr.thedreamtable.farm.alert.FarmAlerts.read(snapshot));

		assertEquals(4, areas.alerts(instanceId).orElseThrow().active(FarmAlertType.BLOCKS_MISSING).orElseThrow().quantity());
	}
}
