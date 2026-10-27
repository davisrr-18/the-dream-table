package com.davisr.thedreamtable.farm;

import com.davisr.thedreamtable.GameSetup;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepairOperationTest {
	private static final UUID OWNER = UUID.fromString("10000000-0000-0000-0000-000000000001");
	private static final UUID OPERATOR = UUID.fromString("20000000-0000-0000-0000-000000000002");
	private static final UUID OPERATION = UUID.fromString("30000000-0000-0000-0000-000000000003");
	private static final BlockPos TABLE = new BlockPos(4, 64, 8);

	@BeforeAll
	static void bootstrap() {
		GameSetup.bootstrap();
	}

	private static FarmAreas areas() {
		FarmAreas areas = new FarmAreas();
		FarmSite site = new FarmSite(TABLE, new BlockPos(10, 64, 10), Direction.NORTH);
		Map<UUID, FarmGrant> grants = Map.of(OPERATOR, new FarmGrant(OPERATOR, FarmRole.OPERATOR, 10L));
		areas.recordFinished(OWNER, FarmTemplates.get(FarmOptions.SUGAR_CANE.id()), site, Map.of(), UUID.randomUUID(), grants);
		UUID farm = areas.finishedBy(OWNER).getFirst().instanceId();
		areas.setCustomName(farm, "River Works");
		areas.setDirty(false);
		return areas;
	}

	@Test
	void repairLeaseIsAtomicAndRequiresTheMatchingToken() {
		FarmAreas areas = areas();
		UUID farm = areas.finishedBy(OWNER).getFirst().instanceId();

		assertTrue(areas.beginRepair(farm, OPERATOR, TABLE, OPERATION).isPresent());
		assertTrue(areas.beginRepair(farm, OWNER, TABLE, UUID.randomUUID()).isEmpty());
		assertEquals(FarmOperation.REPAIRING, areas.finished(farm).orElseThrow().operation());
		assertFalse(areas.completeRepair(farm, UUID.randomUUID()));
		assertEquals(FarmOperation.REPAIRING, areas.finished(farm).orElseThrow().operation());
		assertTrue(areas.completeRepair(farm, OPERATION));
		assertEquals(FarmOperation.READY, areas.finished(farm).orElseThrow().operation());
	}

	@Test
	void leaseRoundTripPreservesOwnershipIdentityAndMetadata() {
		FarmAreas areas = areas();
		FarmAreas.Finished before = areas.finishedBy(OWNER).getFirst();
		areas.beginRepair(before.instanceId(), OPERATOR, TABLE, OPERATION).orElseThrow();

		FarmAreas restored = FarmAreas.read(areas.writeNbt(new NbtCompound(), null), null);
		FarmAreas.Finished after = restored.finished(before.instanceId()).orElseThrow();

		assertEquals(OWNER, after.ownerUuid());
		assertEquals(before.instanceId(), after.instanceId());
		assertEquals("River Works", after.customName());
		assertEquals(before.grants(), after.grants());
		assertEquals(FarmOperation.REPAIRING, after.operation());
		assertEquals(new RepairLease(OPERATION, OPERATOR, TABLE), after.repairLease());
	}

	@Test
	void revocationDoesNotCancelARepairThatAlreadyStarted() {
		FarmAreas areas = areas();
		UUID farm = areas.finishedBy(OWNER).getFirst().instanceId();
		areas.beginRepair(farm, OPERATOR, TABLE, OPERATION).orElseThrow();

		assertTrue(areas.removeGrant(farm, OPERATOR));
		assertEquals(FarmOperation.REPAIRING, areas.finished(farm).orElseThrow().operation());
		assertTrue(areas.completeRepair(farm, OPERATION));
	}

	@Test
	void onlyProvenOrphansReturnToReady() {
		FarmAreas areas = areas();
		UUID farm = areas.finishedBy(OWNER).getFirst().instanceId();
		areas.beginRepair(farm, OPERATOR, TABLE, OPERATION).orElseThrow();

		assertEquals(0, areas.failOrphanedRepairs(lease -> true));
		assertEquals(FarmOperation.REPAIRING, areas.finished(farm).orElseThrow().operation());
		assertEquals(1, areas.failOrphanedRepairs(lease -> false));
		assertEquals(FarmOperation.READY, areas.finished(farm).orElseThrow().operation());
	}
}
