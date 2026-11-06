package com.davisr.thedreamtable.block.entity;

import com.davisr.thedreamtable.GameSetup;
import com.davisr.thedreamtable.farm.FarmStep;
import com.davisr.thedreamtable.farm.FarmTemplate;
import net.minecraft.block.Blocks;
import net.minecraft.inventory.SimpleInventory;
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
	void aFarmWithAllItemsUsedHoldsNothingLeft() {
		MaterialLedger ledger = new MaterialLedger();
		ledger.accept(farm, new ItemStack(Items.STONE, 2), false);
		assertTrue(ledger.anyLeft());
		ledger.consume(farm, Items.STONE);
		assertTrue(ledger.anyLeft());
		ledger.consume(farm, Items.STONE);
		assertFalse(ledger.anyLeft());
		assertTrue(ledger.anyDelivered(), "used items still count as progress while the build is kept");
	}

	@Test
	void aKeptProjectForgetsUsedItemsAndKeepsTheUnusedOnes() {
		MaterialLedger ledger = new MaterialLedger();
		ledger.accept(farm, new ItemStack(Items.STONE, 10), false);
		ledger.accept(farm, new ItemStack(Items.GLASS, 3), false);
		for (int i = 0; i < 4; i++) {
			ledger.consume(farm, Items.STONE);
		}
		for (int i = 0; i < 3; i++) {
			ledger.consume(farm, Items.GLASS);
		}
		ledger.forgetUsed();
		NbtCompound nbt = new NbtCompound();
		ledger.write(nbt);
		MaterialLedger loaded = new MaterialLedger();
		loaded.read(nbt);
		assertEquals(6, loaded.stored(farm, Items.STONE));
		assertEquals(4, loaded.remaining(farm, Items.STONE), "the restarted build still needs what was used");
		assertEquals(3, loaded.remaining(farm, Items.GLASS));
		assertEquals(0, loaded.supplied(1));
		assertEquals(3, loaded.accept(farm, new ItemStack(Items.GLASS, 5), false));
	}

	@Test
	void aFinishedFarmWithNothingLeftIsUntouchedOnceUsedItemsAreForgotten() {
		MaterialLedger ledger = new MaterialLedger();
		ledger.accept(farm, new ItemStack(Items.STONE, 10), false);
		for (int i = 0; i < 10; i++) {
			ledger.consume(farm, Items.STONE);
		}
		ledger.forgetUsed();
		assertFalse(ledger.anyDelivered());
		assertEquals(10, ledger.accept(farm, new ItemStack(Items.STONE, 64), false));
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
	void twoMaterialsSwappedToTheSameItemKeepFillingAfterOneHasBeenUsed() {
		List<FarmStep> steps = new ArrayList<>();
		for (int x = 0; x < 3; x++) {
			steps.add(FarmStep.block(x, 0, 0, Blocks.STONE.getDefaultState(), Items.STONE));
			steps.add(FarmStep.block(x, 1, 0, Blocks.COBBLESTONE.getDefaultState(), Items.COBBLESTONE));
		}
		FarmTemplate both = FarmTemplate.of("both", steps);
		MaterialLedger ledger = new MaterialLedger();
		int stone = 0;
		assertEquals(Items.STONE, both.materials().get(stone).item());
		assertTrue(ledger.swap(both, stone, ledger.swapOptions(both, stone).indexOf(Items.COBBLESTONE)));

		assertEquals(3, ledger.accept(both, new ItemStack(Items.COBBLESTONE, 3), false));
		for (int step = 0; step < 3; step++) {
			assertTrue(ledger.consume(both, Items.STONE));
		}
		assertEquals(3, ledger.remaining(both, Items.COBBLESTONE));
		assertEquals(3, ledger.accept(both, new ItemStack(Items.COBBLESTONE, 3), false));
		assertEquals(0, ledger.deposited(0), "the used material takes no more than it needs");
		assertEquals(3, ledger.deposited(1));
		assertTrue(ledger.has(both, Items.COBBLESTONE));
		assertEquals(0, ledger.remaining(both, Items.COBBLESTONE));
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

	@Test
	void leftoverDepositsAreReturnedOnceAndNeverFreeOnes() {
		MaterialLedger ledger = new MaterialLedger();
		ledger.accept(farm, new ItemStack(Items.STONE, 2), true);
		ledger.accept(farm, new ItemStack(Items.STONE, 6), false);
		ledger.consume(farm, Items.STONE);
		assertEquals(6, count(ledger.drain(farm)));
		assertEquals(0, count(ledger.drain(farm)));
		assertFalse(ledger.anyDelivered());
	}

	private static FarmTemplate otherFarm() {
		List<FarmStep> steps = new ArrayList<>();
		for (int x = 0; x < 4; x++) {
			steps.add(FarmStep.block(x, 0, 0, Blocks.DIRT.getDefaultState(), Items.DIRT));
		}
		return FarmTemplate.of("other", steps);
	}

	private static Item only(List<ItemStack> stacks) {
		assertEquals(1, stacks.stream().map(ItemStack::getItem).distinct().count());
		return stacks.get(0).getItem();
	}

	@Test
	void drainWithoutATemplateStillReturnsTheDeliveredItems() {
		MaterialLedger ledger = new MaterialLedger();
		ledger.accept(farm, new ItemStack(Items.STONE, 7), false);
		ledger.accept(farm, new ItemStack(Items.GLASS, 2), false);
		List<ItemStack> back = ledger.drain(null);
		assertEquals(9, count(back));
		assertEquals(7, back.stream().filter(stack -> stack.isOf(Items.STONE)).mapToInt(ItemStack::getCount).sum());
		assertEquals(2, back.stream().filter(stack -> stack.isOf(Items.GLASS)).mapToInt(ItemStack::getCount).sum());
	}

	@Test
	void drainAfterTheDesignChangedReturnsWhatWasDelivered() {
		MaterialLedger ledger = new MaterialLedger();
		ledger.accept(farm, new ItemStack(Items.STONE, 5), false);
		List<ItemStack> back = ledger.drain(otherFarm());
		assertEquals(5, count(back));
		assertEquals(Items.STONE, only(back));
	}

	@Test
	void drainReturnsTheSwappedItemEvenIfTheSwapIsGoneOrTheFarmIsNull() {
		MaterialLedger ledger = new MaterialLedger();
		int option = ledger.swapOptions(farm, 0).indexOf(Items.COBBLESTONE);
		assertTrue(ledger.swap(farm, 0, option));
		ledger.accept(farm, new ItemStack(Items.COBBLESTONE, 4), false);
		ledger.restoreSwaps(java.util.Map.of());
		assertEquals(Items.COBBLESTONE, only(ledger.drain(null)));
	}

	@Test
	void deliveredItemsSurviveSaveAndLoad() {
		MaterialLedger ledger = new MaterialLedger();
		ledger.accept(farm, new ItemStack(Items.STONE, 6), false);
		NbtCompound nbt = new NbtCompound();
		ledger.write(nbt);
		MaterialLedger loaded = new MaterialLedger();
		loaded.read(nbt);
		assertEquals(Items.STONE, only(loaded.drain(null)));
	}

	@Test
	void savesWithoutTheDeliveredKeyFallBackToTheTemplate() {
		MaterialLedger ledger = new MaterialLedger();
		ledger.accept(farm, new ItemStack(Items.STONE, 6), false);
		NbtCompound nbt = new NbtCompound();
		ledger.write(nbt);
		nbt.remove("DeliveredItems");
		MaterialLedger loaded = new MaterialLedger();
		loaded.read(nbt);
		assertEquals(6, loaded.deposited(0));
		List<ItemStack> back = loaded.drain(farm);
		assertEquals(6, count(back));
		assertEquals(Items.STONE, only(back));
		// Neither record nor template: the items are unknown and nothing can be returned.
		MaterialLedger unknown = new MaterialLedger();
		unknown.read(nbt);
		assertEquals(0, count(unknown.drain(null)));
	}

	@Test
	void unknownItemIdsInTheDeliveredKeyAreIgnored() {
		MaterialLedger ledger = new MaterialLedger();
		ledger.accept(farm, new ItemStack(Items.STONE, 2), false);
		NbtCompound nbt = new NbtCompound();
		ledger.write(nbt);
		net.minecraft.nbt.NbtList list = new net.minecraft.nbt.NbtList();
		list.add(net.minecraft.nbt.NbtString.of("the_dream_table:gone"));
		nbt.put("DeliveredItems", list);
		MaterialLedger loaded = new MaterialLedger();
		loaded.read(nbt);
		assertEquals(Items.STONE, only(loaded.drain(farm)));
	}

	@Test
	void itemsDeliveredForAnotherDesignAreNotUsedForTheNewOne() {
		MaterialLedger ledger = new MaterialLedger();
		ledger.accept(farm, new ItemStack(Items.STONE, 3), false);
		// The design under index 0 became dirt: the stone is not dirt, and new dirt must not mix into it.
		FarmTemplate other = otherFarm();
		assertFalse(ledger.has(other, Items.DIRT));
		assertFalse(ledger.consume(other, Items.DIRT));
		assertEquals(0, ledger.stored(other, Items.DIRT));
		assertEquals(0, ledger.accept(other, new ItemStack(Items.DIRT, 4), false));
		assertEquals(Items.STONE, only(ledger.drain(other)));
	}

	@Test
	void absorbNeverExceedsWhatTheFarmNeeds() {
		MaterialLedger ledger = new MaterialLedger();
		SimpleInventory slots = new SimpleInventory(FarmTemplate.MAX_SLOTS);
		slots.setStack(0, new ItemStack(Items.STONE, 64));
		ledger.absorb(farm, slots);
		assertEquals(10, ledger.deposited(0));
		assertEquals(54, slots.getStack(0).getCount());
		// Used items still count towards the need.
		ledger.consume(farm, Items.STONE);
		ledger.absorb(farm, slots);
		assertEquals(10, ledger.supplied(0));
		assertEquals(54, slots.getStack(0).getCount());
	}

	@Test
	void absorbTakesAFullStackWhenThereIsRoomAndEmptiesTheSlot() {
		MaterialLedger ledger = new MaterialLedger();
		SimpleInventory slots = new SimpleInventory(FarmTemplate.MAX_SLOTS);
		slots.setStack(1, new ItemStack(Items.GLASS, 3));
		ledger.absorb(farm, slots);
		assertEquals(3, ledger.deposited(1));
		assertTrue(slots.getStack(1).isEmpty());
		assertEquals(Items.GLASS, only(ledger.drain(null)));
	}

	@Test
	void readClampsMalformedCounts() {
		NbtCompound nbt = new NbtCompound();
		int[] deposited = new int[FarmTemplate.MAX_SLOTS];
		deposited[0] = Integer.MAX_VALUE;
		deposited[1] = -5;
		int[] consumed = new int[FarmTemplate.MAX_SLOTS];
		consumed[0] = Integer.MIN_VALUE;
		consumed[1] = Integer.MAX_VALUE;
		int[] free = new int[FarmTemplate.MAX_SLOTS];
		free[0] = Integer.MAX_VALUE;
		free[1] = 9;
		nbt.putIntArray("Deposited", deposited);
		nbt.putIntArray("Consumed", consumed);
		nbt.putIntArray("FreeDeposited", free);
		MaterialLedger ledger = new MaterialLedger();
		ledger.read(nbt);
		assertEquals(MaterialLedger.MAX_COUNT, ledger.deposited(0));
		assertEquals(0, ledger.deposited(1));
		assertEquals(MaterialLedger.MAX_COUNT, ledger.supplied(1));
		// Creative items are never returned, so a bogus free count cannot hand out more than was deposited.
		assertEquals(0, count(ledger.drain(farm)));
	}

	@Test
	void validSavesAreUnchangedByTheClamp() {
		MaterialLedger ledger = new MaterialLedger();
		ledger.accept(farm, new ItemStack(Items.STONE, 10), false);
		ledger.consume(farm, Items.STONE);
		NbtCompound nbt = new NbtCompound();
		ledger.write(nbt);
		MaterialLedger loaded = new MaterialLedger();
		loaded.read(nbt);
		assertEquals(9, loaded.deposited(0));
		assertEquals(10, loaded.supplied(0));
	}

	private static FarmTemplate single(String id, net.minecraft.block.Block block, Item item, int count) {
		List<FarmStep> steps = new ArrayList<>();
		for (int x = 0; x < count; x++) {
			steps.add(FarmStep.block(x, 0, 0, block.getDefaultState(), item));
		}
		return FarmTemplate.of(id, steps);
	}

	@Test
	void deliveriesForAnotherDesignDoNotMakeTheMaterialsComplete() {
		FarmTemplate dirt = single("dirt", Blocks.DIRT, Items.DIRT, 3);
		FarmTemplate stone = single("stone", Blocks.STONE, Items.STONE, 3);
		MaterialLedger ledger = new MaterialLedger();
		ledger.accept(dirt, new ItemStack(Items.DIRT, 3), false);
		assertTrue(ledger.complete(dirt));
		assertFalse(ledger.complete(stone));
		assertEquals(3, ledger.deposited(dirt, 0));
		assertEquals(0, ledger.deposited(stone, 0));
		assertEquals(0, ledger.supplied(stone, 0));
	}

	@Test
	void incompatibleDeliveriesAreHandedBackSoTheNewDesignCanBeDelivered() {
		FarmTemplate dirt = single("dirt", Blocks.DIRT, Items.DIRT, 3);
		FarmTemplate stone = single("stone", Blocks.STONE, Items.STONE, 3);
		MaterialLedger ledger = new MaterialLedger();
		ledger.accept(dirt, new ItemStack(Items.DIRT, 3), false);
		assertEquals(0, ledger.accept(stone, new ItemStack(Items.STONE, 3), false));
		List<ItemStack> back = ledger.drainIncompatible(stone);
		assertEquals(3, count(back));
		assertEquals(Items.DIRT, only(back));
		assertEquals(3, ledger.accept(stone, new ItemStack(Items.STONE, 3), false));
		assertTrue(ledger.complete(stone));
	}

	@Test
	void drainIncompatibleReturnsOnlyUnspentIncompatibleItemsAndKeepsTheRest() {
		List<FarmStep> steps = new ArrayList<>();
		for (int x = 0; x < 5; x++) {
			steps.add(FarmStep.block(x, 0, 0, Blocks.STONE.getDefaultState(), Items.STONE));
		}
		for (int x = 0; x < 4; x++) {
			steps.add(FarmStep.block(x, 1, 0, Blocks.DIRT.getDefaultState(), Items.DIRT));
		}
		FarmTemplate before = FarmTemplate.of("before", steps);
		int dirt = 0;
		int stone = 1;
		for (int index = 0; index < before.materials().size(); index++) {
			if (before.materials().get(index).item() == Items.DIRT) {
				dirt = index;
			} else {
				stone = index;
			}
		}
		MaterialLedger ledger = new MaterialLedger();
		ledger.accept(before, new ItemStack(Items.STONE, 5), false);
		ledger.accept(before, new ItemStack(Items.DIRT, 3), false);
		ledger.accept(before, new ItemStack(Items.DIRT, 1), true);
		ledger.consume(before, Items.DIRT);
		// The design now needs glass where it needed dirt, and still needs stone.
		List<FarmStep> changed = new ArrayList<>();
		for (int x = 0; x < 5; x++) {
			changed.add(FarmStep.block(x, 0, 0, Blocks.STONE.getDefaultState(), Items.STONE));
		}
		for (int x = 0; x < 4; x++) {
			changed.add(FarmStep.block(x, 1, 0, Blocks.GLASS.getDefaultState(), Items.GLASS));
		}
		FarmTemplate after = FarmTemplate.of("after", changed);
		assertEquals(before.materials().get(stone).item(), after.materials().get(stone).item());
		assertEquals(Items.GLASS, after.materials().get(dirt).item());

		List<ItemStack> back = ledger.drainIncompatible(after);
		// Dirt: 3 survival and 1 Creative delivered, and the Creative one is used first; only the 3 unspent survival ones are handed back.
		assertEquals(Items.DIRT, only(back));
		assertEquals(3, count(back));
		assertEquals(0, ledger.deposited(dirt));
		assertEquals(0, ledger.supplied(dirt));
		// Stone is untouched.
		assertEquals(5, ledger.deposited(stone));
		assertEquals(5, count(ledger.drain(after)));
		assertEquals(0, count(ledger.drainIncompatible(after)));
	}

	@Test
	void drainIncompatibleLeavesLegacyDeliveriesAlone() {
		MaterialLedger ledger = new MaterialLedger();
		ledger.accept(farm, new ItemStack(Items.STONE, 4), false);
		NbtCompound nbt = new NbtCompound();
		ledger.write(nbt);
		nbt.remove("DeliveredItems");
		MaterialLedger legacy = new MaterialLedger();
		legacy.read(nbt);
		assertEquals(0, count(legacy.drainIncompatible(otherFarm())));
		assertEquals(4, legacy.deposited(0));
	}

	/** A broken table saves the session and then empties it; the saved copy must not follow the live counts. */
	@Test
	void aSavedCopyKeepsItsCountsWhenTheLedgerIsEmptiedAfterwards() {
		MaterialLedger ledger = new MaterialLedger();
		assertEquals(4, ledger.accept(farm, new ItemStack(Items.STONE, 4), false));
		NbtCompound saved = new NbtCompound();
		ledger.write(saved);
		ledger.reset();
		MaterialLedger back = new MaterialLedger();
		back.read(saved);
		assertEquals(4, back.deposited(0));
	}

	/** The same holds for every count the ledger saves: consumed and creative-free deliveries too. */
	@Test
	void everySavedCountStaysWhenTheLedgerIsEmptiedAfterwards() {
		MaterialLedger ledger = new MaterialLedger();
		ledger.accept(farm, new ItemStack(Items.STONE, 3), false);
		ledger.accept(farm, new ItemStack(Items.STONE, 2), true);
		// Creative items are used first: one free, one consumed and four deposited are saved.
		assertFalse(ledger.consume(farm, Items.STONE));
		NbtCompound saved = new NbtCompound();
		ledger.write(saved);
		NbtCompound before = saved.copy();
		ledger.reset();
		assertEquals(before, saved);
	}
}
