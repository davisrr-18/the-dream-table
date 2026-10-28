package com.davisr.thedreamtable.farm;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FarmPreferencesTest {
	private static final UUID ALICE = UUID.fromString("10000000-0000-0000-0000-000000000001");
	private static final UUID BOB = UUID.fromString("20000000-0000-0000-0000-000000000002");
	private static final FarmKey FIRST = key("30000000-0000-0000-0000-000000000003");
	private static final FarmKey SECOND = key("40000000-0000-0000-0000-000000000004");

	private static FarmKey key(String uuid) {
		return new FarmKey(Identifier.of("minecraft", "overworld"), UUID.fromString(uuid));
	}

	@Test
	void roundTripKeepsFavoritesTrackersAndAcknowledgementCursorsPerPlayer() {
		FarmPreferences preferences = new FarmPreferences();
		preferences.setFavorite(ALICE, FIRST, true);
		preferences.setFavorite(BOB, SECOND, true);
		preferences.track(ALICE, FIRST);
		preferences.track(ALICE, SECOND);
		preferences.acknowledge(ALICE, FIRST, 17L);
		preferences.acknowledge(ALICE, FIRST, 12L);

		FarmPreferences restored = FarmPreferences.read(preferences.writeNbt(new NbtCompound(), null), null);

		assertTrue(restored.isFavorite(ALICE, FIRST));
		assertFalse(restored.isFavorite(ALICE, SECOND));
		assertTrue(restored.isFavorite(BOB, SECOND));
		assertEquals(SECOND, restored.tracked(ALICE).orElseThrow());
		assertEquals(17L, restored.acknowledgement(ALICE, FIRST));
	}

	@Test
	void protectMyFarmsIsOffByDefaultAndWorldsWithoutItReadAsOff() {
		FarmPreferences preferences = new FarmPreferences();
		assertFalse(preferences.protectsFarms(ALICE));
		NbtCompound untouched = preferences.writeNbt(new NbtCompound(), null);
		assertFalse(untouched.contains("Protecting"));
		assertFalse(FarmPreferences.read(untouched, null).protectsFarms(ALICE));
		assertFalse(FarmPreferences.read(new NbtCompound(), null).protectsFarms(ALICE));
	}

	@Test
	void protectMyFarmsRoundTripsPerPlayerAndAddsToOtherPreferences() {
		FarmPreferences preferences = new FarmPreferences();
		preferences.setFavorite(BOB, FIRST, true);
		preferences.setDirty(false);
		assertTrue(preferences.setProtectsFarms(ALICE, true));
		assertTrue(preferences.isDirty());
		preferences.setDirty(false);
		assertTrue(preferences.setProtectsFarms(ALICE, true));
		assertFalse(preferences.isDirty());

		FarmPreferences restored = FarmPreferences.read(preferences.writeNbt(new NbtCompound(), null), null);

		assertTrue(restored.protectsFarms(ALICE));
		assertFalse(restored.protectsFarms(BOB));
		assertTrue(restored.isFavorite(BOB, FIRST));
	}

	@Test
	void turningProtectMyFarmsOffForgetsIt() {
		FarmPreferences preferences = new FarmPreferences();
		preferences.setProtectsFarms(ALICE, true);
		preferences.setDirty(false);
		assertTrue(preferences.setProtectsFarms(ALICE, false));
		assertTrue(preferences.isDirty());
		assertFalse(FarmPreferences.read(preferences.writeNbt(new NbtCompound(), null), null).protectsFarms(ALICE));
	}

	@Test
	void protectMyFarmsSurvivesPruningOfFarmRelationships() {
		FarmPreferences preferences = new FarmPreferences();
		preferences.setProtectsFarms(ALICE, true);
		preferences.setFavorite(ALICE, FIRST, true);
		preferences.prune((player, key) -> false);
		assertTrue(preferences.protectsFarms(ALICE));
	}

	@Test
	void revocationRemovesOnlyThatPlayersRelationshipToTheFarm() {
		FarmPreferences preferences = new FarmPreferences();
		preferences.setFavorite(ALICE, FIRST, true);
		preferences.setFavorite(BOB, FIRST, true);
		preferences.track(ALICE, FIRST);
		preferences.acknowledge(ALICE, FIRST, 20L);
		preferences.setDirty(false);

		preferences.removeAccess(ALICE, FIRST);

		assertFalse(preferences.isFavorite(ALICE, FIRST));
		assertTrue(preferences.tracked(ALICE).isEmpty());
		assertEquals(0L, preferences.acknowledgement(ALICE, FIRST));
		assertTrue(preferences.isFavorite(BOB, FIRST));
		assertTrue(preferences.isDirty());
	}

	@Test
	void farmRemovalCleansEveryPlayersOrphanedPreferences() {
		FarmPreferences preferences = new FarmPreferences();
		preferences.setFavorite(ALICE, FIRST, true);
		preferences.setFavorite(BOB, FIRST, true);
		preferences.track(BOB, FIRST);
		preferences.acknowledge(ALICE, FIRST, 9L);
		preferences.setDirty(false);

		preferences.removeFarm(FIRST);

		assertFalse(preferences.isFavorite(ALICE, FIRST));
		assertFalse(preferences.isFavorite(BOB, FIRST));
		assertTrue(preferences.tracked(BOB).isEmpty());
		assertEquals(0L, preferences.acknowledgement(ALICE, FIRST));
		assertTrue(preferences.isDirty());
	}

	@Test
	void pruneRemovesOnlyRelationshipsThatAreNoLongerAccessible() {
		FarmPreferences preferences = new FarmPreferences();
		preferences.setFavorite(ALICE, FIRST, true);
		preferences.setFavorite(ALICE, SECOND, true);
		preferences.track(ALICE, FIRST);
		preferences.acknowledge(ALICE, FIRST, 9L);
		preferences.setDirty(false);

		assertEquals(1, preferences.prune((player, farm) -> farm.equals(SECOND)));

		assertFalse(preferences.isFavorite(ALICE, FIRST));
		assertTrue(preferences.isFavorite(ALICE, SECOND));
		assertTrue(preferences.tracked(ALICE).isEmpty());
		assertEquals(0L, preferences.acknowledgement(ALICE, FIRST));
		assertTrue(preferences.isDirty());
	}

	@Test
	void hostileSavedCollectionsAreBoundedAndInvalidCursorsAreIgnored() {
		NbtCompound nbt = new NbtCompound();
		NbtCompound player = new NbtCompound();
		player.putUuid("Player", ALICE);
		NbtList favorites = new NbtList();
		for (int index = 0; index < 5000; index++) {
			favorites.add(NbtString.of(new FarmKey(Identifier.of("minecraft", "overworld"), new UUID(0L, index + 1L)).externalForm()));
		}
		player.put("Favorites", favorites);
		NbtList cursors = new NbtList();
		NbtCompound invalidCursor = new NbtCompound();
		invalidCursor.putString("Farm", FIRST.externalForm());
		invalidCursor.putLong("Cursor", -10L);
		cursors.add(invalidCursor);
		player.put("Acknowledgements", cursors);
		NbtList players = new NbtList();
		players.add(player);
		nbt.put("Players", players);

		FarmPreferences restored = FarmPreferences.read(nbt, null);
		NbtList savedFavorites = restored.writeNbt(new NbtCompound(), null).getList("Players", 10)
				.getCompound(0).getList("Favorites", 8);

		assertEquals(4096, savedFavorites.size());
		assertEquals(0L, restored.acknowledgement(ALICE, FIRST));
	}

	@Test
	void clearingAnAbsentFavoriteDoesNotCreateAnEmptyPlayerRecord() {
		FarmPreferences preferences = new FarmPreferences();

		preferences.setFavorite(ALICE, FIRST, false);

		assertEquals(0, preferences.writeNbt(new NbtCompound(), null).getList("Players", 10).size());
		assertFalse(preferences.isDirty());
	}

	@Test
	void mutationLimitsPreventAcceptedPreferencesFromBeingTruncatedOnSave() {
		FarmPreferences preferences = new FarmPreferences();
		for (int index = 0; index < FarmPreferences.MAX_SAVED_FARMS_PER_PLAYER; index++) {
			FarmKey key = new FarmKey(Identifier.of("minecraft", "overworld"), new UUID(40L, index + 1L));
			assertTrue(preferences.setFavorite(ALICE, key, true));
		}
		FarmKey overflow = new FarmKey(Identifier.of("minecraft", "overworld"), new UUID(40L, Long.MAX_VALUE));

		assertFalse(preferences.setFavorite(ALICE, overflow, true));
		NbtList saved = preferences.writeNbt(new NbtCompound(), null).getList("Players", 10)
				.getCompound(0).getList("Favorites", 8);
		assertEquals(FarmPreferences.MAX_SAVED_FARMS_PER_PLAYER, saved.size());
	}
}
