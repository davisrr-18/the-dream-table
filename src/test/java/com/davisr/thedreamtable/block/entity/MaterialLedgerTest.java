package com.davisr.thedreamtable.block.entity;

import com.davisr.thedreamtable.GameSetup;
import com.davisr.thedreamtable.farm.FarmStep;
import com.davisr.thedreamtable.farm.FarmTemplate;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaterialLedgerTest {
	private static FarmTemplate farm;

	@BeforeAll
	static void setUp() {
		GameSetup.bootstrap();
		List<FarmStep> steps = new ArrayList<>();
		for (int x = 0; x < 10; x++) {
			steps.add(FarmStep.block(x, 0, 0, Blocks.STONE.getDefaultState(), Items.STONE));
		}
		for (int x = 0; x < 3; x++) {
			steps.add(FarmStep.block(x, 1, 0, Blocks.GLASS.getDefaultState(), Items.GLASS));
		}
		farm = FarmTemplate.of("test", steps);
	}

	private static int count(List<ItemStack> stacks) {
		return stacks.stream().mapToInt(ItemStack::getCount).sum();
	}

	@Test
	void acceptsOnlyWhatTheFarmStillNeeds() {
		MaterialLedger ledger = new MaterialLedger();
		assertEquals(10, ledger.accept(farm, new ItemStack(Items.STONE, 64), false));
		assertEquals(0, ledger.accept(farm, new ItemStack(Items.STONE, 64), false));
		assertEquals(0, ledger.accept(farm, new ItemStack(Items.DIRT, 64), false));
		assertEquals(3, ledger.accept(farm, new ItemStack(Items.GLASS, 5), false));
		assertTrue(ledger.complete(farm));
	}

	@Test
	void usedItemsStillCountTowardsWhatIsNeeded() {
		MaterialLedger ledger = new MaterialLedger();
		ledger.accept(farm, new ItemStack(Items.STONE, 4), false);
		assertTrue(ledger.consume(farm, Items.STONE));
		assertEquals(4, ledger.stored(farm, Items.STONE));
		assertEquals(6, ledger.accept(farm, new ItemStack(Items.STONE, 64), false));
	}

	@Test
	void discardReturnsOnlyUnusedItems() {
		MaterialLedger ledger = new MaterialLedger();
		ledger.accept(farm, new ItemStack(Items.STONE, 10), false);
		ledger.consume(farm, Items.STONE);
		ledger.consume(farm, Items.STONE);
		assertEquals(8, count(ledger.drain(farm)));
		assertFalse(ledger.anyDelivered());
	}

	@Test
	void creativeItemsAreUsedFirstAndNeverReturned() {
		MaterialLedger ledger = new MaterialLedger();
		ledger.accept(farm, new ItemStack(Items.STONE, 3), true);
		ledger.accept(farm, new ItemStack(Items.STONE, 5), false);
		assertFalse(ledger.consume(farm, Items.STONE));
		assertFalse(ledger.consume(farm, Items.STONE));
		assertFalse(ledger.consume(farm, Items.STONE));
		assertTrue(ledger.consume(farm, Items.STONE));
		assertEquals(4, count(ledger.drain(farm)));
	}

	@Test
	void onlyCreativeItemsLeftReturnsNothing() {
		MaterialLedger ledger = new MaterialLedger();
		ledger.accept(farm, new ItemStack(Items.GLASS, 3), true);
		assertEquals(0, count(ledger.drain(farm)));
	}

	@Test
	void consumingWithNothingDeliveredFails() {
		MaterialLedger ledger = new MaterialLedger();
		assertFalse(ledger.has(farm, Items.STONE));
		assertFalse(ledger.consume(farm, Items.STONE));
	}

	@Test
	void swapsChangeTheDeliveredItemButKeepTheCounts() {
		MaterialLedger ledger = new MaterialLedger();
		int stone = 0;
		List<Item> options = ledger.swapOptions(farm, stone);
		int option = options.indexOf(Items.COBBLESTONE);
		assertTrue(option >= 0, "cobblestone should be a full-block swap for stone");
		assertTrue(ledger.swap(farm, stone, option));
		assertEquals(Items.COBBLESTONE, ledger.resolve(Items.STONE));
		assertEquals(0, ledger.accept(farm, new ItemStack(Items.STONE, 5), false));
		assertEquals(5, ledger.accept(farm, new ItemStack(Items.COBBLESTONE, 5), false));
		assertEquals(5, ledger.stored(farm, Items.STONE));
		assertEquals(Items.COBBLESTONE, ledger.drain(farm).get(0).getItem());
	}

	@Test
	void saveAndLoadKeepsCountsCreativeItemsAndSwaps() {
		MaterialLedger ledger = new MaterialLedger();
		ledger.accept(farm, new ItemStack(Items.STONE, 2), true);
		ledger.accept(farm, new ItemStack(Items.STONE, 3), false);
		ledger.consume(farm, Items.STONE);
		NbtCompound nbt = new NbtCompound();
		ledger.write(nbt);

		MaterialLedger loaded = new MaterialLedger();
		loaded.read(nbt);
		assertEquals(4, loaded.deposited(0));
		assertEquals(5, loaded.supplied(0));
		assertEquals(3, count(loaded.drain(farm)));
	}
}
