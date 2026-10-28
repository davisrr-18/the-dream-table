package com.davisr.thedreamtable.farm;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import com.davisr.thedreamtable.GameSetup;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.BitSet;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class FarmAreaKeyTest {
	@BeforeAll
	static void setUp() {
		GameSetup.bootstrap();
	}

	@Test
	void simultaneousDismantlesAtOneTableReserveDifferentAreas() {
		UUID player = UUID.nameUUIDFromBytes("OfflinePlayer:Davi".getBytes());
		BlockPos table = new BlockPos(0, 64, 0);
		FarmDismantler first = job(player, new FarmSite(table, new BlockPos(10, 64, 10), Direction.NORTH));
		FarmDismantler second = job(player, new FarmSite(table, new BlockPos(30, 64, 30), Direction.NORTH));

		assertNotEquals(first.areaKey(), second.areaKey());
	}

	@Test
	void aLegacyDismantleReleasesItsOriginalReservation() {
		UUID player = UUID.nameUUIDFromBytes("OfflinePlayer:Davi".getBytes());
		BlockPos table = new BlockPos(0, 64, 0);
		FarmDismantler original = job(player, new FarmSite(table, new BlockPos(10, 64, 10), Direction.NORTH));
		NbtCompound saved = new NbtCompound();
		original.write(saved);
		saved.remove("AreaSpot");

		FarmDismantler legacy = FarmDismantler.read(saved);

		assertNotEquals(original.areaKey(), legacy.areaKey());
		assertEquals(new FarmAreas.Key(table.asLong(), player, FarmAreas.Kind.DISMANTLE, 0L), legacy.areaKey());
	}

	@Test
	void aTakeDownJobKeepsItsHostTableAndDesignThroughASave() {
		UUID player = UUID.nameUUIDFromBytes("OfflinePlayer:Davi".getBytes());
		BlockPos original = new BlockPos(0, 64, 0);
		BlockPos host = new BlockPos(50, 64, 50);
		FarmSite site = new FarmSite(original, new BlockPos(10, 64, 10), Direction.NORTH);
		FarmTemplate cane = FarmTemplates.get(FarmOptions.SUGAR_CANE.id());
		FarmTemplate layout = new FarmTemplate(FarmTemplates.newSavedId(cane.id(), cane.signature()), cane.steps(), cane.materials(), cane.center());
		BitSet placed = new BitSet();
		placed.set(0, 2);
		FarmDismantler job = new FarmDismantler(player, layout, site, Map.of(), placed, new BitSet(), host, identity(player));
		NbtCompound saved = new NbtCompound();
		job.write(saved);

		assertNull(FarmTemplates.get(layout.id()));
		FarmDismantler restored = FarmDismantler.read(saved);

		assertEquals(new FarmAreas.Key(host.asLong(), player, FarmAreas.Kind.DISMANTLE, site.anchor().asLong()), restored.areaKey());
		assertEquals(job.areaKey(), restored.areaKey());
		FarmTemplate again = FarmTemplates.get(layout.id());
		assertNotNull(again);
		assertEquals(layout.signature(), again.signature());
		assertEquals(cane.steps(), again.steps());
	}

	@Test
	void aJobSavedWithoutAHostOrDesignStillReadsAsBefore() {
		UUID player = UUID.nameUUIDFromBytes("OfflinePlayer:Davi".getBytes());
		BlockPos table = new BlockPos(0, 64, 0);
		FarmDismantler original = job(player, new FarmSite(table, new BlockPos(10, 64, 10), Direction.NORTH));
		NbtCompound saved = new NbtCompound();
		original.write(saved);
		saved.remove("Host");

		assertEquals(original.areaKey(), FarmDismantler.read(saved).areaKey());
		assertFalse(saved.contains("Layout"));
	}

	@Test
	void aTakeDownJobKeepsTheFarmIdentityAndTheStandingStepsThroughASave() {
		UUID player = UUID.nameUUIDFromBytes("OfflinePlayer:Davi".getBytes());
		FarmSite site = new FarmSite(new BlockPos(0, 64, 0), new BlockPos(10, 64, 10), Direction.NORTH);
		FarmTemplate cane = FarmTemplates.get(FarmOptions.SUGAR_CANE.id());
		FarmTemplate layout = new FarmTemplate(FarmTemplates.newSavedId(cane.id(), cane.signature()), cane.steps(), cane.materials(), cane.center());
		FarmDismantler job = new FarmDismantler(player, layout, site, Map.of(), new BitSet(), new BitSet(), site.table(), identity(player));
		NbtCompound saved = new NbtCompound();
		job.write(saved);

		FarmDismantler.Identity restored = FarmDismantler.read(saved).identity();

		assertNotNull(restored);
		assertEquals(job.identity(), restored);
		assertEquals("Sugar Farm", restored.customName());
	}

	@Test
	void aJobSavedBeforeIdentitiesReadsWithoutOne() {
		UUID player = UUID.nameUUIDFromBytes("OfflinePlayer:Davi".getBytes());
		FarmDismantler original = job(player, new FarmSite(new BlockPos(0, 64, 0), new BlockPos(10, 64, 10), Direction.NORTH));
		NbtCompound saved = new NbtCompound();
		original.write(saved);

		assertNull(FarmDismantler.read(saved).identity());
	}

	@Test
	void anUnreadableJobIsSkippedButItsClaimIsStillKnown() {
		UUID player = UUID.nameUUIDFromBytes("OfflinePlayer:Davi".getBytes());
		BlockPos host = new BlockPos(50, 64, 50);
		FarmSite site = new FarmSite(new BlockPos(0, 64, 0), new BlockPos(10, 64, 10), Direction.NORTH);
		FarmTemplate cane = FarmTemplates.get(FarmOptions.SUGAR_CANE.id());
		FarmTemplate layout = new FarmTemplate(FarmTemplates.newSavedId(cane.id(), cane.signature()), cane.steps(), cane.materials(), cane.center());
		FarmDismantler job = new FarmDismantler(player, layout, site, Map.of(), new BitSet(), new BitSet(), host, identity(player));
		NbtCompound saved = new NbtCompound();
		job.write(saved);
		NbtCompound broken = new NbtCompound();
		broken.putIntArray("State", new int[]{99});
		broken.putIntArray("X", new int[]{0});
		broken.putIntArray("Y", new int[]{0});
		broken.putIntArray("Z", new int[]{0});
		broken.putIntArray("Item", new int[]{0});
		saved.put("Layout", broken);

		assertNull(FarmDismantler.read(saved));
		assertEquals(job.areaKey(), FarmDismantler.claimOf(saved));
	}

	private static FarmDismantler.Identity identity(UUID owner) {
		return new FarmDismantler.Identity(UUID.nameUUIDFromBytes("farm".getBytes()), owner, "Sugar Farm", Map.of(), Map.of());
	}

	private static FarmDismantler job(UUID player, FarmSite site) {
		return new FarmDismantler(player, "test:farm", 1L, site, Map.of(), new BitSet(), new BitSet(), 0);
	}
}
