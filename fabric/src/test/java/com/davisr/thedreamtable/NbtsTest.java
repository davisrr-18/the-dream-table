package com.davisr.thedreamtable;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.nbt.NbtInt;
import net.minecraft.nbt.NbtIntArray;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registries;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The helpers that keep 1.21.1's save format after 1.21.11 removed the old NBT calls. */
class NbtsTest {
	private static final UUID SAMPLE = UUID.fromString("00000001-0002-0003-0004-000000000005");

	@BeforeAll
	static void bootstrap() {
		GameSetup.bootstrap();
	}

	@Test
	void aUuidIsSavedAsAnIntArrayOfFour() {
		NbtCompound nbt = new NbtCompound();
		Nbts.putUuid(nbt, "Player", SAMPLE);

		// The 1.21.1 layout: most significant long, then least significant long, each as high int then low int.
		assertTrue(nbt.get("Player") instanceof NbtIntArray);
		assertArrayEquals(new int[] {1, 0x00020003, 0x00040000, 5}, Nbts.ints(nbt, "Player"));
		assertEquals(SAMPLE, Nbts.getUuid(nbt, "Player"));
	}

	@Test
	void aUuidSavedByTheGameReadsBack() {
		NbtCompound nbt = new NbtCompound();
		nbt.putIntArray("Player", new int[] {1, 0x00020003, 0x00040000, 5});

		assertTrue(Nbts.containsUuid(nbt, "Player"));
		assertEquals(SAMPLE, Nbts.getUuid(nbt, "Player"));
	}

	@Test
	void negativeIntsKeepTheirBitsInAUuid() {
		UUID id = new UUID(0xFFFFFFFF80000000L, 0x00000001FFFFFFFFL);
		NbtCompound nbt = new NbtCompound();
		Nbts.putUuid(nbt, "Id", id);

		assertEquals(id, Nbts.getUuid(nbt, "Id"));
	}

	@Test
	void aMissingOrMalformedUuidThrowsAndIsNotContained() {
		NbtCompound nbt = new NbtCompound();
		nbt.putIntArray("Short", new int[] {1, 2, 3});
		nbt.putString("Text", SAMPLE.toString());

		assertFalse(Nbts.containsUuid(nbt, "Missing"));
		assertFalse(Nbts.containsUuid(nbt, "Short"));
		// The string form is not a saved UUID here: reading it must fail, never guess.
		assertFalse(Nbts.containsUuid(nbt, "Text"));
		assertThrows(IllegalArgumentException.class, () -> Nbts.getUuid(nbt, "Missing"));
		assertThrows(IllegalArgumentException.class, () -> Nbts.getUuid(nbt, "Short"));
		assertThrows(IllegalArgumentException.class, () -> Nbts.getUuid(nbt, "Text"));
	}

	@Test
	void typedChecksMatchTheOldContains() {
		NbtCompound nbt = new NbtCompound();
		nbt.putInt("Int", 1);
		nbt.putLong("Long", 2L);
		nbt.putString("Text", "x");
		nbt.put("List", new NbtList());
		nbt.put("Compound", new NbtCompound());
		nbt.putLongArray("Longs", new long[] {1L});

		assertTrue(Nbts.hasNumber(nbt, "Int"));
		assertTrue(Nbts.hasNumber(nbt, "Long"));
		assertFalse(Nbts.hasNumber(nbt, "Text"));
		assertTrue(Nbts.hasString(nbt, "Text"));
		assertFalse(Nbts.hasString(nbt, "Int"));
		assertTrue(Nbts.hasList(nbt, "List"));
		assertFalse(Nbts.hasList(nbt, "Compound"));
		assertTrue(Nbts.hasCompound(nbt, "Compound"));
		assertTrue(Nbts.hasLongArray(nbt, "Longs"));
		assertFalse(Nbts.hasIntArray(nbt, "Longs"));
		assertFalse(Nbts.hasList(nbt, "Missing"));
	}

	@Test
	void missingArraysReadEmpty() {
		NbtCompound nbt = new NbtCompound();
		nbt.putString("Text", "not an array");

		assertEquals(0, Nbts.ints(nbt, "Missing").length);
		assertEquals(0, Nbts.ints(nbt, "Text").length);
		assertEquals(0, Nbts.longs(nbt, "Missing").length);
	}

	@Test
	void aTypedListIsEmptyUnlessEveryElementHasTheType() {
		NbtCompound nbt = new NbtCompound();
		NbtList doubles = new NbtList();
		doubles.add(NbtDouble.of(1.0));
		doubles.add(NbtDouble.of(2.0));
		NbtList mixed = new NbtList();
		mixed.add(NbtDouble.of(1.0));
		mixed.add(NbtInt.of(2));
		nbt.put("Doubles", doubles);
		nbt.put("Mixed", mixed);

		assertEquals(2, Nbts.list(nbt, "Doubles", NbtDouble.class).size());
		assertTrue(Nbts.list(nbt, "Doubles", NbtInt.class).isEmpty());
		assertTrue(Nbts.list(nbt, "Mixed", NbtDouble.class).isEmpty());
		assertTrue(Nbts.list(nbt, "Missing", NbtDouble.class).isEmpty());
	}

	@Test
	void aMalformedEntryFailsTheWholeListRead() {
		NbtCompound nbt = new NbtCompound();
		NbtList names = new NbtList();
		names.add(NbtString.of("a"));
		names.add(NbtInt.of(7));
		NbtList entries = new NbtList();
		entries.add(new NbtCompound());
		entries.add(NbtString.of("not a compound"));
		nbt.put("Names", names);
		nbt.put("Entries", entries);

		assertThrows(IllegalArgumentException.class, () -> Nbts.strings(nbt, "Names"));
		assertThrows(IllegalArgumentException.class, () -> Nbts.compounds(nbt, "Entries"));
		assertEquals(List.of(), Nbts.strings(nbt, "Missing"));
		assertEquals(List.of(), Nbts.compounds(nbt, "Missing"));
	}

	@Test
	void itemStacksRoundTripAsPlainIdAndCountCompounds() {
		List<ItemStack> stacks = List.of(new ItemStack(Items.LAPIS_LAZULI, 5), ItemStack.EMPTY, new ItemStack(Items.OAK_PLANKS, 3));

		NbtList saved = Nbts.writeItems(stacks, lookup());

		// Empty stacks are not written, and a stack is the {id, count} compound 1.21.1 wrote.
		assertEquals(2, saved.size());
		assertEquals("minecraft:lapis_lazuli", saved.getCompoundOrEmpty(0).getString("id", ""));
		assertEquals(5, saved.getCompoundOrEmpty(0).getInt("count", 0));
		List<ItemStack> back = Nbts.readItems(saved, lookup(), DataFixes.current());
		assertEquals(2, back.size());
		assertTrue(ItemStack.areEqual(new ItemStack(Items.LAPIS_LAZULI, 5), back.get(0)));
		assertTrue(ItemStack.areEqual(new ItemStack(Items.OAK_PLANKS, 3), back.get(1)));
	}

	@Test
	void anItemThatNoLongerReadsIsSkippedNotFatal() {
		NbtList saved = new NbtList();
		NbtCompound gone = new NbtCompound();
		gone.putString("id", "the_dream_table:no_such_item");
		gone.putInt("count", 1);
		saved.add(gone);
		NbtCompound fine = new NbtCompound();
		fine.putString("id", "minecraft:paper");
		fine.putInt("count", 2);
		saved.add(fine);

		List<ItemStack> back = Nbts.readItems(saved, lookup(), DataFixes.current());

		assertEquals(1, back.size());
		assertTrue(ItemStack.areEqual(new ItemStack(Items.PAPER, 2), back.get(0)));
	}

	@Test
	void aStackSavedByAnOlderGameUnderARenamedItemStillReads() {
		NbtList saved = new NbtList();
		NbtCompound chain = new NbtCompound();
		chain.putString("id", "minecraft:chain");
		chain.putInt("count", 4);
		saved.add(chain);

		// As a 1.21.1 save (the stamp a table without one is read with), the chain is the iron chain now.
		List<ItemStack> back = Nbts.readItems(saved, lookup(), DataFixes.ASSUMED);

		assertEquals(1, back.size());
		assertTrue(ItemStack.areEqual(new ItemStack(Items.IRON_CHAIN, 4), back.get(0)));
	}

	@Test
	void aListOfTheWrongElementTypeReadsEmptyLikeBefore() {
		NbtCompound nbt = new NbtCompound();
		NbtList names = new NbtList();
		names.add(NbtString.of("not a compound"));
		nbt.put("Areas", names);

		assertTrue(Nbts.list(nbt, "Areas", NbtCompound.class).isEmpty());
		assertEquals(1, Nbts.list(nbt, "Areas", NbtString.class).size());
	}

	/** Static registries resolve items; the empty manager resolves none, so encoding a stack through it fails. */
	private static DynamicRegistryManager lookup() {
		return DynamicRegistryManager.of(Registries.REGISTRIES);
	}
}
