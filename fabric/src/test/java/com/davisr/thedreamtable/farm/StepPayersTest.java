package com.davisr.thedreamtable.farm;

import com.davisr.thedreamtable.GameSetup;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.BitSet;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A Take Down refunds each paid step to whoever paid it; nobody recorded means the farm's owner, as before payers existed. */
class StepPayersTest {
	private static final UUID OWNER = UUID.fromString("10000000-0000-0000-0000-000000000001");
	private static final UUID OPERATOR = UUID.fromString("20000000-0000-0000-0000-000000000002");
	private static final UUID OTHER = UUID.fromString("30000000-0000-0000-0000-000000000003");
	private static final FarmSite SITE = new FarmSite(new BlockPos(0, 64, 0), new BlockPos(10, 64, 10), Direction.NORTH);

	@BeforeAll
	static void bootstrap() {
		GameSetup.bootstrap();
	}

	private static BitSet steps(int... indexes) {
		BitSet set = new BitSet();
		for (int index : indexes) {
			set.set(index);
		}
		return set;
	}

	@Test
	void noRecordMeansTheOwnerPaid() {
		assertTrue(StepPayers.NONE.isEmpty());
		assertNull(StepPayers.NONE.payerOf(3));
		assertEquals(OWNER, StepPayers.NONE.payerOf(3, OWNER));
	}

	@Test
	void anOperatorRepairRecordsOnlyTheStepsTheyPaidFor() {
		StepPayers payers = StepPayers.NONE.afterPlacing(OPERATOR, OWNER, steps(2, 3, 4), steps(2, 4));
		assertEquals(OPERATOR, payers.payerOf(2));
		assertNull(payers.payerOf(3));
		assertEquals(OPERATOR, payers.payerOf(4, OWNER));
		assertEquals(OWNER, payers.payerOf(3, OWNER));
		assertEquals(OWNER, payers.payerOf(9, OWNER));
	}

	@Test
	void theOwnersOwnRepairRecordsNothing() {
		assertSame(StepPayers.NONE, StepPayers.NONE.afterPlacing(OWNER, OWNER, steps(1, 2), steps(1, 2)));
	}

	@Test
	void aStepPlacedAgainTakesTheNewPayment() {
		StepPayers first = StepPayers.NONE.afterPlacing(OPERATOR, OWNER, steps(2, 3), steps(2, 3));
		StepPayers ownerRepairs = first.afterPlacing(OWNER, OWNER, steps(2), steps(2));
		assertNull(ownerRepairs.payerOf(2));
		assertEquals(OPERATOR, ownerRepairs.payerOf(3));
		StepPayers freeReplacement = first.afterPlacing(OPERATOR, OWNER, steps(3), new BitSet());
		assertNull(freeReplacement.payerOf(3));
		assertEquals(OPERATOR, freeReplacement.payerOf(2));
		StepPayers another = first.afterPlacing(OTHER, OWNER, steps(3), steps(3));
		assertEquals(OPERATOR, another.payerOf(2));
		assertEquals(OTHER, another.payerOf(3));
	}

	@Test
	void splitGivesEachPaidStepToItsPayerExactlyOnce() {
		StepPayers payers = StepPayers.NONE.afterPlacing(OPERATOR, OWNER, steps(2, 4), steps(2, 4));
		BitSet refunded = steps(1, 2, 3, 4);
		Map<UUID, BitSet> split = payers.split(refunded, OWNER);
		assertEquals(steps(2, 4), split.get(OPERATOR));
		assertEquals(steps(1, 3), split.get(OWNER));
		BitSet together = new BitSet();
		split.values().forEach(together::or);
		assertEquals(refunded, together);
		assertEquals(refunded.cardinality(), split.values().stream().mapToInt(BitSet::cardinality).sum());
		assertEquals(Map.of(OWNER, refunded), StepPayers.NONE.split(refunded, OWNER));
	}

	@Test
	void splitIgnoresPayersOfStepsNotRefunded() {
		StepPayers payers = StepPayers.NONE.afterPlacing(OPERATOR, OWNER, steps(7), steps(7));
		assertEquals(Map.of(OWNER, steps(1)), payers.split(steps(1), OWNER));
	}

	@Test
	void restrictingKeepsOnlyTheStepsLeftStanding() {
		StepPayers payers = StepPayers.NONE.afterPlacing(OPERATOR, OWNER, steps(2, 3), steps(2, 3));
		StepPayers left = payers.restrictedTo(steps(3, 5));
		assertNull(left.payerOf(2));
		assertEquals(OPERATOR, left.payerOf(3));
		assertTrue(payers.restrictedTo(steps(8)).isEmpty());
	}

	@Test
	void nbtRoundTripsAndWritesNothingWhenEmpty() {
		StepPayers payers = StepPayers.NONE.afterPlacing(OPERATOR, OWNER, steps(2, 70), steps(2, 70))
				.afterPlacing(OTHER, OWNER, steps(5), steps(5));
		NbtCompound nbt = new NbtCompound();
		payers.write(nbt, "PaidBy");
		assertEquals(payers, StepPayers.read(nbt, "PaidBy"));
		assertEquals(OPERATOR, StepPayers.read(nbt, "PaidBy").payerOf(70));

		NbtCompound empty = new NbtCompound();
		StepPayers.NONE.write(empty, "PaidBy");
		assertFalse(empty.contains("PaidBy"));
		assertSame(StepPayers.NONE, StepPayers.read(empty, "PaidBy"));
	}

	@Test
	void malformedEntriesReadAsNoPayer() {
		NbtCompound nbt = new NbtCompound();
		NbtList list = new NbtList();
		NbtCompound noSteps = new NbtCompound();
		noSteps.putUuid("Player", OPERATOR);
		list.add(noSteps);
		list.add(new NbtCompound());
		nbt.put("PaidBy", list);
		assertTrue(StepPayers.read(nbt, "PaidBy").isEmpty());
		nbt.putString("PaidBy", "junk");
		assertTrue(nbt.contains("PaidBy", NbtElement.STRING_TYPE));
		assertTrue(StepPayers.read(nbt, "PaidBy").isEmpty());
	}

	@Test
	void aFinishedFarmSavedWithoutPayersReadsAsPaidByTheOwner() {
		FarmTemplate template = FarmTemplates.get(FarmOptions.SUGAR_CANE.id());
		FarmAreas areas = new FarmAreas();
		areas.recordFinished(OWNER, template, SITE, Map.of());
		NbtCompound saved = areas.writeNbt(new NbtCompound(), null);
		// The additive key is absent, exactly what 1.0.0 and earlier 1.1.0 saves look like.
		assertFalse(saved.getList("Areas", NbtElement.COMPOUND_TYPE).getCompound(0).contains("PaidBy"));
		FarmAreas.Finished farm = FarmAreas.read(saved, null).finishedBy(OWNER).getFirst();
		assertTrue(farm.payers().isEmpty());
		assertEquals(OWNER, farm.payers().payerOf(0, OWNER));
	}

	@Test
	void anOperatorsRepairIsRecordedInTheFarmAndSurvivesASave() {
		FarmTemplate template = FarmTemplates.get(FarmOptions.SUGAR_CANE.id());
		FarmAreas areas = new FarmAreas();
		areas.recordFinished(OWNER, template, SITE, Map.of());
		UUID instanceId = areas.finishedBy(OWNER).getFirst().instanceId();

		areas.mergePlacement(instanceId, OWNER, OPERATOR, steps(0, 1), steps(0));

		FarmAreas.Finished farm = FarmAreas.read(areas.writeNbt(new NbtCompound(), null), null).finishedBy(OWNER).getFirst();
		assertEquals(OPERATOR, farm.payers().payerOf(0));
		assertNull(farm.payers().payerOf(1));
		assertTrue(farm.paid().get(0));
		assertFalse(farm.paid().get(1));
		// Step 1 was placed again without an item, so nobody is refunded for it; step 0 goes to the operator, the rest to the owner.
		assertEquals(OWNER, farm.payers().payerOf(2, OWNER));
	}
}
