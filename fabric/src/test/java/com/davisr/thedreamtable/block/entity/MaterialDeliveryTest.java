package com.davisr.thedreamtable.block.entity;

import com.davisr.thedreamtable.GameSetup;
import com.davisr.thedreamtable.farm.FarmStep;
import com.davisr.thedreamtable.farm.FarmTemplate;
import com.davisr.thedreamtable.network.TableNoticePayload;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Bulk delivery moves exactly what the farm still needs, and never a stack whose data a refund would destroy. */
class MaterialDeliveryTest {
	private static FarmTemplate farm;

	@Test
	void onlyAFullDeliveryIsPlainInformation() {
		assertEquals(TableNoticePayload.Severity.INFO, MaterialDelivery.severity(new MaterialDelivery.Tally(5, 0)));
		assertEquals(TableNoticePayload.Severity.PROBLEM, MaterialDelivery.severity(new MaterialDelivery.Tally(5, 2)));
		assertEquals(TableNoticePayload.Severity.PROBLEM, MaterialDelivery.severity(new MaterialDelivery.Tally(0, 1)));
		assertEquals(TableNoticePayload.Severity.PROBLEM, MaterialDelivery.severity(MaterialDelivery.Tally.NONE));
	}

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

	private static MaterialDelivery.Receiver receiver(MaterialLedger ledger) {
		return new MaterialDelivery.Receiver() {
			@Override
			public int accept(ItemStack stack) {
				return ledger.accept(farm, stack, false);
			}

			@Override
			public boolean wants(ItemStack stack) {
				return ledger.remaining(farm, stack.getItem()) > 0;
			}
		};
	}

	private static ItemStack named(ItemStack stack) {
		stack.setCustomName(Text.literal("Precious"));
		return stack;
	}

	@Test
	void movesExactlyWhatIsNeededAndLeavesTheRest() {
		MaterialLedger ledger = new MaterialLedger();
		SimpleInventory source = new SimpleInventory(5);
		source.setStack(0, new ItemStack(Items.STONE, 64));
		source.setStack(1, new ItemStack(Items.DIRT, 20));
		source.setStack(2, new ItemStack(Items.GLASS, 2));
		MaterialDelivery.Tally tally = MaterialDelivery.pull(source, MaterialDelivery.ALL_SLOTS, receiver(ledger));
		assertEquals(12, tally.delivered());
		assertEquals(0, tally.refused());
		assertEquals(54, source.getStack(0).getCount());
		assertEquals(20, source.getStack(1).getCount());
		assertTrue(source.getStack(2).isEmpty());
		assertEquals(10, ledger.deposited(farm, 0));
		assertEquals(2, ledger.deposited(farm, 1));
	}

	@Test
	void repeatingCannotOverDeliver() {
		MaterialLedger ledger = new MaterialLedger();
		SimpleInventory source = new SimpleInventory(3);
		source.setStack(0, new ItemStack(Items.STONE, 64));
		source.setStack(1, new ItemStack(Items.GLASS, 64));
		assertEquals(13, MaterialDelivery.pull(source, MaterialDelivery.ALL_SLOTS, receiver(ledger)).delivered());
		assertEquals(0, MaterialDelivery.pull(source, MaterialDelivery.ALL_SLOTS, receiver(ledger)).delivered());
		assertEquals(54, source.getStack(0).getCount());
		assertEquals(61, source.getStack(1).getCount());
		assertTrue(ledger.complete(farm));
	}

	@Test
	void spreadsAcrossStacksUntilTheNeedIsMet() {
		MaterialLedger ledger = new MaterialLedger();
		SimpleInventory source = new SimpleInventory(3);
		source.setStack(0, new ItemStack(Items.STONE, 4));
		source.setStack(1, new ItemStack(Items.STONE, 4));
		source.setStack(2, new ItemStack(Items.STONE, 4));
		assertEquals(10, MaterialDelivery.pull(source, MaterialDelivery.ALL_SLOTS, receiver(ledger)).delivered());
		assertTrue(source.getStack(0).isEmpty());
		assertTrue(source.getStack(1).isEmpty());
		assertEquals(2, source.getStack(2).getCount());
	}

	@Test
	void stacksWithDataComponentsAreRefusedAndCounted() {
		MaterialLedger ledger = new MaterialLedger();
		SimpleInventory source = new SimpleInventory(4);
		source.setStack(0, named(new ItemStack(Items.STONE, 5)));
		source.setStack(1, named(new ItemStack(Items.DIRT, 5)));
		source.setStack(2, new ItemStack(Items.GLASS, 3));
		MaterialDelivery.Tally tally = MaterialDelivery.pull(source, MaterialDelivery.ALL_SLOTS, receiver(ledger));
		assertEquals(3, tally.delivered());
		assertEquals(1, tally.refused(), "only the named stone is needed; the named dirt is not the farm's business");
		assertEquals(5, source.getStack(0).getCount());
		assertEquals(5, source.getStack(1).getCount());
		assertEquals(0, ledger.deposited(farm, 0));
	}

	@Test
	void aShulkerBoxWithContentsIsNeverDelivered() {
		ItemStack box = new ItemStack(Items.SHULKER_BOX);
		box.getOrCreateSubNbt("BlockEntityTag").put("Items", new SimpleInventory(new ItemStack(Items.DIAMOND, 3)).toNbtList());
		assertFalse(MaterialLedger.plain(box));
		assertTrue(MaterialLedger.plain(new ItemStack(Items.SHULKER_BOX)));
		ItemStack emptyNbt = new ItemStack(Items.SHULKER_BOX);
		emptyNbt.getOrCreateNbt();
		assertTrue(MaterialLedger.plain(emptyNbt), "an empty compound carries no data to lose");
		List<FarmStep> steps = List.of(FarmStep.block(0, 0, 0, Blocks.SHULKER_BOX.getDefaultState(), Items.SHULKER_BOX));
		FarmTemplate boxes = FarmTemplate.of("boxes", steps);
		MaterialLedger ledger = new MaterialLedger();
		assertEquals(0, ledger.accept(boxes, box, false));
		assertEquals(1, ledger.accept(boxes, new ItemStack(Items.SHULKER_BOX), false));
	}

	@Test
	void everyEntryPointRefusesDataComponents() {
		MaterialLedger ledger = new MaterialLedger();
		assertEquals(0, ledger.accept(farm, named(new ItemStack(Items.STONE, 10)), false));
		assertEquals(0, ledger.accept(farm, named(new ItemStack(Items.STONE, 10)), true));
		SimpleInventory slots = new SimpleInventory(2);
		slots.setStack(0, named(new ItemStack(Items.STONE, 10)));
		ledger.absorb(farm, slots);
		assertEquals(10, slots.getStack(0).getCount(), "an old slot keeps a named stack so it is handed back intact");
		assertFalse(ledger.anyDelivered());
		slots.setStack(0, new ItemStack(Items.STONE, 10));
		ledger.absorb(farm, slots);
		assertTrue(slots.getStack(0).isEmpty());
		assertTrue(ledger.anyDelivered());
	}

	@Test
	void remainingCountsUsedItemsAndIgnoresComponents() {
		MaterialLedger ledger = new MaterialLedger();
		ledger.accept(farm, new ItemStack(Items.STONE, 4), false);
		ledger.consume(farm, Items.STONE);
		assertEquals(6, ledger.remaining(farm, Items.STONE));
		assertEquals(0, ledger.remaining(farm, Items.DIRT));
	}

	@Test
	void playerSlotsAreTheMainInventoryAndOffhandOnly() {
		assertTrue(MaterialDelivery.PLAYER_SLOTS.test(0));
		assertTrue(MaterialDelivery.PLAYER_SLOTS.test(PlayerInventory.MAIN_SIZE - 1));
		for (int armor = PlayerInventory.MAIN_SIZE; armor < PlayerInventory.OFF_HAND_SLOT; armor++) {
			assertFalse(MaterialDelivery.PLAYER_SLOTS.test(armor));
		}
		assertTrue(MaterialDelivery.PLAYER_SLOTS.test(PlayerInventory.OFF_HAND_SLOT));
	}

	@Test
	void summaryNamesWhatWasSkipped() {
		assertEquals("gui.the_dream_table.delivered", key(MaterialDelivery.summary(new MaterialDelivery.Tally(5, 0), false, 0)));
		assertEquals("gui.the_dream_table.delivered_refused", key(MaterialDelivery.summary(new MaterialDelivery.Tally(5, 2), false, 0)));
		assertEquals("gui.the_dream_table.delivery_refused", key(MaterialDelivery.summary(new MaterialDelivery.Tally(0, 2), true, 3)));
		assertEquals("gui.the_dream_table.deliver_none", key(MaterialDelivery.summary(MaterialDelivery.Tally.NONE, false, 0)));
		assertEquals("gui.the_dream_table.deliver_nearby_none", key(MaterialDelivery.summary(MaterialDelivery.Tally.NONE, true, 0)));
		assertEquals("gui.the_dream_table.deliver_nearby_none_skipped", key(MaterialDelivery.summary(MaterialDelivery.Tally.NONE, true, 2)));
	}

	private static String key(Text text) {
		return ((net.minecraft.text.TranslatableTextContent) text.getContent()).getKey();
	}
}
