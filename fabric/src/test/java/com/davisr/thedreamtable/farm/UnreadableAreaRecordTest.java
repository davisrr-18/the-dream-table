package com.davisr.thedreamtable.farm;

import com.davisr.thedreamtable.GameSetup;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A finished farm record that cannot be read still holds its area, and is saved again exactly as it was. */
class UnreadableAreaRecordTest {
	private static final UUID OWNER = UUID.fromString("10000000-0000-0000-0000-000000000001");
	private static final UUID INSTANCE = UUID.fromString("50000000-0000-0000-0000-000000000005");

	@BeforeAll
	static void setUp() {
		GameSetup.bootstrap();
	}

	/** A saved finished farm whose player entry is missing, so it can't be read but keeps its saved box. */
	private static NbtCompound unreadableSave() {
		FarmAreas areas = new FarmAreas();
		FarmSite site = new FarmSite(new BlockPos(0, 64, 0), new BlockPos(10, 64, 10), Direction.NORTH);
		areas.recordFinished(OWNER, FarmTemplates.get(FarmOptions.SUGAR_CANE.id()), site, Map.of(), INSTANCE, "My Cane");
		NbtCompound nbt = areas.writeNbt(new NbtCompound(), null);
		nbt.getList("Areas", NbtElement.COMPOUND_TYPE).getCompound(0).remove("Player");
		return nbt;
	}

	@Test
	void anUnreadableRecordStillHoldsItsBox() {
		NbtCompound nbt = unreadableSave();
		FarmAreas read = FarmAreas.read(nbt, null);
		int[] saved = nbt.getList("Areas", NbtElement.COMPOUND_TYPE).getCompound(0).getIntArray("Box");
		Box area = new Box(saved[0], saved[1], saved[2], saved[3], saved[4], saved[5]);
		assertEquals(1, read.unreadableCount());
		assertTrue(read.unreadableCovers(box -> box.intersects(area)));
		assertTrue(read.unreadableCovers(box -> box.contains(area.getCenter())));
		assertFalse(read.unreadableCovers(box -> box.intersects(new Box(-1000, 0, -1000, -999, 1, -999))));
	}

	@Test
	void theRecordIsSavedAsItWas() {
		NbtCompound nbt = unreadableSave();
		NbtCompound original = nbt.getList("Areas", NbtElement.COMPOUND_TYPE).getCompound(0).copy();
		NbtCompound back = FarmAreas.read(nbt, null).writeNbt(new NbtCompound(), null);
		assertEquals(original, back.getList("Areas", NbtElement.COMPOUND_TYPE).getCompound(0));
		assertEquals(1, back.getList("Areas", NbtElement.COMPOUND_TYPE).size());
	}
}
