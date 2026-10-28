package com.davisr.thedreamtable.block.entity;

import com.davisr.thedreamtable.GameSetup;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.block.entity.TrappedChestBlockEntity;
import net.minecraft.component.ComponentChanges;
import net.minecraft.component.ComponentMap;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.inventory.ContainerLock;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.LootTables;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The containers "Deliver from Nearby" may touch, decided without a world. */
class NearbyContainersTest {
	private static final BlockPos TABLE = new BlockPos(100, 64, 100);

	@BeforeAll
	static void setUp() {
		GameSetup.bootstrap();
	}

	@Test
	void reachIsASixBlockBallAroundTheTable() {
		assertTrue(NearbyContainers.withinRange(TABLE, TABLE.add(6, 0, 0)));
		assertTrue(NearbyContainers.withinRange(TABLE, TABLE.add(0, -6, 0)));
		assertTrue(NearbyContainers.withinRange(TABLE, TABLE.add(3, 3, 4)));
		assertFalse(NearbyContainers.withinRange(TABLE, TABLE.add(7, 0, 0)));
		assertFalse(NearbyContainers.withinRange(TABLE, TABLE.add(5, 5, 5)));
	}

	@Test
	void onlyChestsBarrelsAndPlacedShulkerBoxesQualify() {
		assertTrue(NearbyContainers.supported(new ChestBlockEntity(TABLE, Blocks.CHEST.getDefaultState())));
		assertTrue(NearbyContainers.supported(new TrappedChestBlockEntity(TABLE, Blocks.TRAPPED_CHEST.getDefaultState())));
		assertTrue(NearbyContainers.supported(new BarrelBlockEntity(TABLE, Blocks.BARREL.getDefaultState())));
		assertTrue(NearbyContainers.supported(new ShulkerBoxBlockEntity(TABLE, Blocks.SHULKER_BOX.getDefaultState())));
		assertFalse(NearbyContainers.supported(new EnderChestBlockEntity(TABLE, Blocks.ENDER_CHEST.getDefaultState())));
		assertFalse(NearbyContainers.supported(new HopperBlockEntity(TABLE, Blocks.HOPPER.getDefaultState())));
	}

	@Test
	void lockedContainersAreSkippedWhoeverHoldsTheKey() {
		ChestBlockEntity open = new ChestBlockEntity(TABLE, Blocks.CHEST.getDefaultState());
		assertFalse(NearbyContainers.locked(open));
		ChestBlockEntity locked = new ChestBlockEntity(TABLE, Blocks.CHEST.getDefaultState());
		locked.readComponents(ComponentMap.builder().add(DataComponentTypes.LOCK, new ContainerLock("key")).build(), ComponentChanges.EMPTY);
		assertTrue(NearbyContainers.locked(locked));
	}

	@Test
	void unopenedLootContainersAreSkipped() {
		ChestBlockEntity chest = new ChestBlockEntity(TABLE, Blocks.CHEST.getDefaultState());
		assertFalse(NearbyContainers.unopenedLoot(chest));
		chest.setLootTable(LootTables.SIMPLE_DUNGEON_CHEST);
		assertTrue(NearbyContainers.unopenedLoot(chest));
		BarrelBlockEntity barrel = new BarrelBlockEntity(TABLE, Blocks.BARREL.getDefaultState());
		barrel.setLootTable(LootTables.SIMPLE_DUNGEON_CHEST);
		assertTrue(NearbyContainers.unopenedLoot(barrel));
	}

	@Test
	void deliveryTakesFromARealChestWithoutTouchingOtherSlots() {
		ChestBlockEntity chest = new ChestBlockEntity(TABLE, Blocks.CHEST.getDefaultState());
		chest.setStack(0, new ItemStack(Items.STONE, 20));
		chest.setStack(1, new ItemStack(Items.DIAMOND, 3));
		int[] taken = {0};
		MaterialDelivery.Tally tally = MaterialDelivery.pull(chest, MaterialDelivery.ALL_SLOTS, new MaterialDelivery.Receiver() {
			@Override
			public int accept(ItemStack stack) {
				int moved = stack.isOf(Items.STONE) ? Math.min(stack.getCount(), 10 - taken[0]) : 0;
				taken[0] += moved;
				return moved;
			}

			@Override
			public boolean wants(ItemStack stack) {
				return stack.isOf(Items.STONE);
			}
		});
		assertEquals(10, tally.delivered());
		assertEquals(10, chest.getStack(0).getCount());
		assertEquals(3, chest.getStack(1).getCount());
	}
}
