package com.davisr.thedreamtable.farm;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class FarmLoaderSelectionTest {
	@Test
	void aStableKeyStillTargetsTheSameFarmAfterTheListChanges() {
		FarmLoader.Located selected = new FarmLoader.Located("overworld|selected", null, null);
		FarmLoader.Located addedLater = new FarmLoader.Located("overworld|newer", null, null);

		assertSame(selected, FarmLoader.findByKey(List.of(addedLater, selected), selected.key()).orElseThrow());
	}

	@Test
	void legacySelectionsMigrateWithoutDroppingExistingPublicKeys() {
		String existing = "minecraft:overworld|12345678-1234-5678-9abc-123456789abc";
		Set<String> picked = new LinkedHashSet<>(List.of("minecraft:overworld|10/20", existing));

		boolean changed = FarmLoader.migrateKeys(picked, Map.of(
				"minecraft:overworld|10/20", "minecraft:overworld|aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
		));

		assertTrue(changed);
		assertEquals(new LinkedHashSet<>(List.of(
				"minecraft:overworld|aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", existing
		)), picked);
	}

	@Test
	void migrationDropsOnlyLegacySelectionsWhoseFarmIsTrulyGone() {
		Set<String> picked = new LinkedHashSet<>(List.of("minecraft:overworld|missing/site"));

		assertTrue(FarmLoader.migrateKeys(picked, Map.of()));
		assertTrue(picked.isEmpty());
	}

	@Test
	void loweringTheQuotaDoesNotDeleteValidSelectionsAboveTheLimit() {
		Set<String> picked = new LinkedHashSet<>(List.of("farm-1", "farm-2", "farm-3"));

		boolean changed = FarmLoader.pruneMissing(picked, Set.copyOf(picked));

		assertFalse(changed);
		assertEquals(3, picked.size());
	}

	@Test
	void forgedOrStaleKeysNeverOccupyAQuotaSlot() {
		FarmLoader loader = new FarmLoader();
		UUID player = UUID.randomUUID();

		assertEquals(FarmLoader.ToggleResult.NOT_FOUND, loader.toggleKnown(player, "forged", false));
		assertFalse(loader.writeNbt(new net.minecraft.nbt.NbtCompound(), null)
				.getCompound("Players").contains(player.toString()));
	}

	@Test
	void theOwnerSelectsAndUnselectsTheirFarm() {
		FarmLoader loader = new FarmLoader();
		UUID owner = UUID.randomUUID();

		assertEquals(FarmLoader.ToggleResult.SELECTED, loader.toggleKnown(owner, "farm", true));
		assertEquals(FarmLoader.ToggleResult.UNSELECTED, loader.toggleKnown(owner, "farm", true));
	}

	@Test
	void ownerQuotaRejectsOnlyNewSelectionsAndKeepsExistingOnesRemovable() {
		FarmLoader loader = new FarmLoader();
		UUID owner = UUID.randomUUID();
		for (int index = 0; index < 3; index++) {
			assertEquals(FarmLoader.ToggleResult.SELECTED,
					loader.toggleKnown(owner, "farm-" + index, true));
		}

		assertEquals(FarmLoader.ToggleResult.OWNER_QUOTA_FULL,
				loader.toggleKnown(owner, "overflow", true));
		assertEquals(FarmLoader.ToggleResult.UNSELECTED,
				loader.toggleKnown(owner, "farm-0", true));
	}
}
