package com.davisr.thedreamtable.farm;

import com.davisr.thedreamtable.GameSetup;
import net.minecraft.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FarmSpawnsBucketTest {
	@BeforeAll
	static void setUp() {
		GameSetup.bootstrap();
	}

	@Test
	void everyBucketThatPoursItsFluidLeavesAnEmptyBucket() {
		assertTrue(FarmSpawns.leavesEmptyBucket(Items.WATER_BUCKET));
		assertTrue(FarmSpawns.leavesEmptyBucket(Items.LAVA_BUCKET));
		assertTrue(FarmSpawns.leavesEmptyBucket(Items.POWDER_SNOW_BUCKET));
	}

	@Test
	void otherItemsKeepBeingConsumed() {
		assertFalse(FarmSpawns.leavesEmptyBucket(Items.BUCKET));
		assertFalse(FarmSpawns.leavesEmptyBucket(Items.STONE));
	}

	@Test
	void newLavaAndPowderSnowStepsAreRecordedUnpaidSoTakeDownDoesNotRefundTheBucketTwice() {
		assertFalse(FarmSpawns.recordsAsPaid(Items.LAVA_BUCKET, true));
		assertFalse(FarmSpawns.recordsAsPaid(Items.POWDER_SNOW_BUCKET, true));
		assertFalse(FarmSpawns.refundsOnTakeDown(Items.LAVA_BUCKET, FarmSpawns.recordsAsPaid(Items.LAVA_BUCKET, true)));
	}

	@Test
	void stepsRecordedPaidBeforeTheChangeStillRefundTheirLavaBucket() {
		// A record saved by v2.2.0-beta marks the consumed lava bucket as paid.
		assertTrue(FarmSpawns.refundsOnTakeDown(Items.LAVA_BUCKET, true));
		assertTrue(FarmSpawns.refundsOnTakeDown(Items.POWDER_SNOW_BUCKET, true));
	}

	@Test
	void waterNeverRefundsAndOrdinaryPaidItemsAlwaysDo() {
		assertFalse(FarmSpawns.refundsOnTakeDown(Items.WATER_BUCKET, true));
		assertTrue(FarmSpawns.refundsOnTakeDown(Items.STONE, FarmSpawns.recordsAsPaid(Items.STONE, true)));
		assertFalse(FarmSpawns.refundsOnTakeDown(Items.STONE, FarmSpawns.recordsAsPaid(Items.STONE, false)));
		assertFalse(FarmSpawns.refundsOnTakeDown(null, true));
		assertTrue(FarmSpawns.recordsAsPaid(null, true));
	}
}
