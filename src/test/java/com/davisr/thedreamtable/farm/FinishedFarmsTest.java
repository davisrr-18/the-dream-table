package com.davisr.thedreamtable.farm;

import com.davisr.thedreamtable.GameSetup;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
