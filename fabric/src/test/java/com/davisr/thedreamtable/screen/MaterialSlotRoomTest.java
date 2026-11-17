package com.davisr.thedreamtable.screen;

import com.davisr.thedreamtable.GameSetup;
import com.davisr.thedreamtable.farm.MaterialSpec;
import net.minecraft.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A material slot holds only what the farm still needs, and refuses items once nothing is missing (v1.4.0 regressions). */
class MaterialSlotRoomTest {
	@BeforeAll
	static void bootstrap() {
		GameSetup.bootstrap();
	}

	@Test
	void roomIsWhatIsStillMissingNeverTheFullCount() {
		MaterialSpec stone = new MaterialSpec(Items.STONE, 64);
		assertEquals(64, MaterialSlot.room(stone, 0));
		assertEquals(4, MaterialSlot.room(stone, 60), "a surplus must not sit in the slot");
		assertEquals(0, MaterialSlot.room(stone, 64));
		assertEquals(0, MaterialSlot.room(stone, 70), "never negative");
		assertEquals(0, MaterialSlot.room(null, 0));
	}

	@Test
	void aFullSlotRefusesInsteadOfAcceptingAZeroItemMove() {
		// Vanilla insertItem counts a 0-item split as a move: accepting with no room made shift-click loop forever.
		assertFalse(MaterialSlot.accepts(true, 0));
		assertTrue(MaterialSlot.accepts(true, 1));
		assertFalse(MaterialSlot.accepts(false, 64));
	}
}
