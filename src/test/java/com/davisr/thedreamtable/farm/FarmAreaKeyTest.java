package com.davisr.thedreamtable.farm;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.Test;

import java.util.BitSet;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class FarmAreaKeyTest {
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

	private static FarmDismantler job(UUID player, FarmSite site) {
		return new FarmDismantler(player, "test:farm", 1L, site, Map.of(), new BitSet(), new BitSet(), 0);
	}
}
