package com.davisr.thedreamtable.farm;

import com.davisr.thedreamtable.DataFixes;
import com.davisr.thedreamtable.GameSetup;
import com.davisr.thedreamtable.Nbts;
import net.minecraft.datafixer.Schemas;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Worlds saved by Minecraft 1.21.1 must keep their farms on 1.21.11. The fixtures are the files 1.21.1 itself wrote
 * (made by the 1.21.1 build of this mod; see {@code fixtures/1.21.1/README.md} for every value), read here the way
 * the game's saved-data manager reads them: data fixer first, then the state type's codec.
 */
class SavedStateFormatTest {
	private static final UUID A = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final UUID B = UUID.fromString("22222222-2222-2222-2222-222222222222");
	private static final UUID FARM_A = UUID.fromString("50000000-0000-0000-0000-000000000001");
	private static final UUID FARM_B = UUID.fromString("50000000-0000-0000-0000-000000000002");
	private static final String KEY_1 = "minecraft:overworld|30000000-0000-0000-0000-000000000001";
	private static final String KEY_2 = "minecraft:the_nether|30000000-0000-0000-0000-000000000002";

	@BeforeAll
	static void setUp() {
		GameSetup.bootstrap();
	}

	private static NbtCompound root(String file) throws IOException {
		try (InputStream in = SavedStateFormatTest.class.getResourceAsStream("/fixtures/1.21.1/" + file)) {
			assertNotNull(in, file + " is missing");
			return NbtIo.readCompressed(in, NbtSizeTracker.ofUnlimitedBytes());
		}
	}

	/** What PersistentStateManager hands the codec: the file's {@code data}, after the type's data fixer ran from the file's version. */
	private static NbtCompound data(String file, PersistentStateType<?> type) throws IOException {
		NbtCompound root = root(file);
		assertEquals(DataFixes.ASSUMED, root.getInt("DataVersion", 0), file + " is a 1.21.1 save");
		NbtCompound fixed = type.dataFixType().update(Schemas.getFixer(), root, DataFixes.ASSUMED, DataFixes.current());
		// The fixer for the type must leave this mod's data exactly as written.
		assertEquals(root.getCompoundOrEmpty("data"), fixed.getCompoundOrEmpty("data"), file + " survives the data fixer");
		return fixed.getCompoundOrEmpty("data");
	}

	private static <T extends PersistentState> T load(String file, PersistentStateType<T> type) throws IOException {
		NbtElement element = data(file, type);
		return type.codec().parse(NbtOps.INSTANCE, element).getOrThrow();
	}

	private static <T extends PersistentState> NbtCompound saved(T state, PersistentStateType<T> type) {
		return (NbtCompound) type.codec().encodeStart(NbtOps.INSTANCE, state).getOrThrow();
	}

	@Test
	void everyStateTypeHasADataFixTypeAndKeepsItsFileName() {
		// A null fix type makes the game's loader throw, swallow it, and start from an empty state that the next save writes over.
		assertNotNull(FarmAreas.TYPE.dataFixType());
		assertNotNull(FarmLoader.TYPE.dataFixType());
		assertNotNull(FarmPreferences.TYPE.dataFixType());
		assertNotNull(ParkedProjects.TYPE.dataFixType());
		assertNotNull(PendingRefunds.TYPE.dataFixType());
		assertEquals(FarmAreas.STATE_KEY, FarmAreas.TYPE.id());
		assertEquals(FarmLoader.STATE_KEY, FarmLoader.TYPE.id());
		assertEquals(FarmPreferences.STATE_KEY, FarmPreferences.TYPE.id());
		assertEquals(ParkedProjects.STATE_KEY, ParkedProjects.TYPE.id());
		assertEquals(PendingRefunds.STATE_KEY, PendingRefunds.TYPE.id());
	}

	@Test
	void farmPreferencesFrom1_21_1ReadAndSaveBackUnchanged() throws IOException {
		FarmPreferences preferences = load("farm_preferences.dat", FarmPreferences.TYPE);

		assertTrue(preferences.protectsFarms(A));
		assertFalse(preferences.protectsFarms(B));
		assertEquals(FarmKey.parse(KEY_1), preferences.tracked(A).orElseThrow());
		assertTrue(preferences.tracked(B).isEmpty());
		assertEquals(17L, preferences.acknowledgement(A, FarmKey.parse(KEY_1)));
		assertEquals(9L, preferences.acknowledgement(B, FarmKey.parse(KEY_2)));
		assertEquals(data("farm_preferences.dat", FarmPreferences.TYPE), saved(preferences, FarmPreferences.TYPE));
	}

	@Test
	void pickedFarmsFrom1_21_1SaveBackUnchanged() throws IOException {
		FarmLoader loader = load("farm_loader.dat", FarmLoader.TYPE);

		NbtCompound back = saved(loader, FarmLoader.TYPE);

		assertEquals(data("farm_loader.dat", FarmLoader.TYPE), back);
		assertEquals(2, back.getCompoundOrEmpty("Players").getListOrEmpty(A.toString()).size());
		assertEquals(1, back.getCompoundOrEmpty("Players").getListOrEmpty(B.toString()).size());
	}

	@Test
	void pendingRefundsFrom1_21_1SaveBackUnchangedIncludingAnUnknownItem() throws IOException {
		PendingRefunds refunds = load("pending_refunds.dat", PendingRefunds.TYPE);

		assertEquals(data("pending_refunds.dat", PendingRefunds.TYPE), saved(refunds, PendingRefunds.TYPE));
	}

	@Test
	void parkedProjectsAndHeldBuildsFrom1_21_1SaveBackUnchanged() throws IOException {
		ParkedProjects parked = load("parked_projects.dat", ParkedProjects.TYPE);

		assertEquals(1, parked.count(A));
		assertEquals(0, parked.count(B));
		assertEquals(data("parked_projects.dat", ParkedProjects.TYPE), saved(parked, ParkedProjects.TYPE));
	}

	@Test
	void finishedFarmsFrom1_21_1KeepEverythingTheyHadIncludingTheUnreadableRecord() throws IOException {
		FarmAreas areas = load("farm_areas.dat", FarmAreas.TYPE);

		assertEquals(1, areas.unreadableCount());
		List<FarmAreas.Finished> ofA = areas.finishedBy(A);
		List<FarmAreas.Finished> ofB = areas.finishedBy(B);
		assertEquals(1, ofA.size());
		assertEquals(1, ofB.size());
		FarmAreas.Finished cane = ofA.getFirst();
		assertEquals(FARM_A, cane.instanceId());
		assertEquals("sugarcane", cane.farmId());
		assertEquals("Riverside Cane", cane.customName());
		assertEquals(-2436952895899130566L, cane.signature());
		assertEquals(Items.SPRUCE_PLANKS, cane.swaps().get(Items.OAK_PLANKS));
		assertEquals(280, cane.layout().steps().size());
		assertEquals(279, cane.placed().cardinality());
		assertFalse(cane.placed().get(2));
		assertEquals(2, cane.paid().cardinality());
		FarmAreas.Finished bamboo = ofB.getFirst();
		assertEquals(FARM_B, bamboo.instanceId());
		assertEquals(5443182533435593035L, bamboo.signature());
		assertEquals(288, bamboo.layout().steps().size());
		assertEquals(null, bamboo.customName());
	}

	@Test
	void everythingButTheDesignsSavesBackUnchanged() throws IOException {
		NbtCompound original = data("farm_areas.dat", FarmAreas.TYPE);

		NbtCompound back = saved(load("farm_areas.dat", FarmAreas.TYPE), FarmAreas.TYPE);

		// A stored design is re-encoded by this game (and stamped with its data version), so it is compared apart below.
		assertEquals(withoutDesigns(original), withoutDesigns(back));
		assertEquals(original.getListOrEmpty("Areas").size(), back.getListOrEmpty("Areas").size());
	}

	@Test
	void aStoredDesignKeepsEveryBlockAndOnlyTheRenamedOnesChangeTheirName() throws IOException {
		NbtCompound original = data("farm_areas.dat", FarmAreas.TYPE);
		NbtCompound back = saved(load("farm_areas.dat", FarmAreas.TYPE), FarmAreas.TYPE);

		for (int index = 0; index < original.getListOrEmpty("Areas").size(); index++) {
			NbtCompound before = original.getListOrEmpty("Areas").getCompoundOrEmpty(index);
			NbtCompound after = back.getListOrEmpty("Areas").getCompoundOrEmpty(index);
			if (!before.contains("Layout")) {
				continue;
			}
			NbtCompound oldDesign = before.getCompoundOrEmpty("Layout");
			NbtCompound newDesign = after.getCompoundOrEmpty("Layout");
			// The unreadable record is kept raw, so its design is untouched; the readable ones are stamped and renamed.
			if (!after.contains("Player") || !before.contains("Player")) {
				assertEquals(oldDesign, newDesign);
				continue;
			}
			assertEquals(DataFixes.current(), newDesign.getInt("DataVersion", 0));
			assertEquals(renamed(paletteNames(oldDesign)), paletteNames(newDesign));
			assertEquals(oldDesign.getListOrEmpty("Items").size(), newDesign.getListOrEmpty("Items").size());
			assertEquals(Nbts.ints(oldDesign, "X").length, Nbts.ints(newDesign, "X").length);
		}
	}

	private static NbtCompound withoutDesigns(NbtCompound areas) {
		NbtCompound copy = areas.copy();
		NbtList list = copy.getListOrEmpty("Areas");
		for (int index = 0; index < list.size(); index++) {
			list.getCompoundOrEmpty(index).remove("Layout");
		}
		return copy;
	}

	private static TreeSet<String> paletteNames(NbtCompound design) {
		TreeSet<String> names = new TreeSet<>();
		for (NbtElement entry : design.getListOrEmpty("Palette")) {
			names.add(((NbtCompound) entry).getString("Name", ""));
		}
		return names;
	}

	private static TreeSet<String> renamed(TreeSet<String> names) {
		TreeSet<String> result = new TreeSet<>();
		for (String name : names) {
			result.add(name.equals("minecraft:chain") ? "minecraft:iron_chain" : name);
		}
		return result;
	}
}
