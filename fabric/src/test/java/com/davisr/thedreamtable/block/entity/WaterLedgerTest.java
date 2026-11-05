package com.davisr.thedreamtable.block.entity;

import com.davisr.thedreamtable.GameSetup;
import com.davisr.thedreamtable.farm.FarmStep;
import com.davisr.thedreamtable.farm.FarmTemplate;
import com.davisr.thedreamtable.farm.MaterialSpec;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A water bucket delivered for a farm that had a water material: once the farm's design has no water material, the bucket
 * is handed back, since nothing can use it at that index any more.
 */
class WaterLedgerTest {
	private static FarmTemplate withWater;
	private static FarmTemplate withoutWater;

	@BeforeAll
	static void setUp() {
		GameSetup.bootstrap();
		List<FarmStep> stone = List.of(
				FarmStep.block(0, 0, 0, Blocks.STONE.getDefaultState(), Items.STONE),
				FarmStep.block(1, 0, 0, Blocks.STONE.getDefaultState(), Items.STONE));
		FarmStep water = FarmStep.block(2, 0, 0, Blocks.WATER.getDefaultState(), Items.WATER_BUCKET);
		List<FarmStep> steps = new ArrayList<>(stone);
		steps.add(water);
		// The design before this change: water was a material, so a bucket delivered for it is counted at its index.
		withWater = new FarmTemplate("with_water", steps,
				List.of(new MaterialSpec(Items.STONE, 2), new MaterialSpec(Items.WATER_BUCKET, 1)), BlockPos.ORIGIN);
		withoutWater = FarmTemplate.of("without_water", stone);
	}

	private static int buckets(List<ItemStack> stacks) {
		return stacks.stream().filter(stack -> stack.isOf(Items.WATER_BUCKET)).mapToInt(ItemStack::getCount).sum();
	}

	@Test
	void theDeliveredBucketIsHandedBackWhenTheFarmHasNoWaterMaterial() {
		MaterialLedger ledger = new MaterialLedger();
		assertEquals(1, ledger.accept(withWater, new ItemStack(Items.WATER_BUCKET), false));

		List<ItemStack> back = ledger.drainIncompatible(withoutWater);
		assertEquals(1, buckets(back));
		assertFalse(ledger.anyDelivered());
	}

	@Test
	void theRecordSurvivesASaveAndStillRefundsTheBucket() {
		MaterialLedger ledger = new MaterialLedger();
		ledger.accept(withWater, new ItemStack(Items.WATER_BUCKET), false);
		NbtCompound saved = new NbtCompound();
		ledger.write(saved);

		MaterialLedger loaded = new MaterialLedger();
		loaded.read(saved);
		assertEquals(1, buckets(loaded.drainIncompatible(withoutWater)));
	}

	@Test
	void aFarmThatStillHasItsWaterMaterialKeepsTheBucket() {
		MaterialLedger ledger = new MaterialLedger();
		ledger.accept(withWater, new ItemStack(Items.WATER_BUCKET), false);

		assertTrue(ledger.drainIncompatible(withWater).isEmpty());
		assertTrue(ledger.anyDelivered());
	}

	@Test
	void aLaterMaterialLeavesTheRecordedBucketHandedBackAndTheCountsIntact() {
		MaterialLedger ledger = new MaterialLedger();
		ledger.accept(withWater, new ItemStack(Items.WATER_BUCKET), false);
		// The design lost its water step, and stone is delivered for the steps that are left.
		assertEquals(2, ledger.accept(withoutWater, new ItemStack(Items.STONE, 2), false));

		List<ItemStack> back = ledger.drainIncompatible(withoutWater);
		assertEquals(1, buckets(back));
		assertTrue(back.stream().noneMatch(stack -> stack.isOf(Items.STONE)));
		assertEquals(2, ledger.deposited(withoutWater, 0));
		assertTrue(ledger.complete(withoutWater));
		assertTrue(ledger.anyDelivered());
	}
}
