package com.davisr.thedreamtable.farm;

import com.davisr.thedreamtable.farm.FarmOptions.FarmOption;
import net.minecraft.world.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A farm may declare the dimension it is designed for; without one it never warns. */
class IntendedDimensionTest {
	@Test
	void onlyTheGoldFarmDeclaresADimensionAndItIsTheNether() {
		for (FarmOption option : FarmOptions.ALL) {
			if (option == FarmOptions.GOLD) {
				assertEquals(World.NETHER, option.intendedDimension());
				assertFalse(option.outsideIntendedDimension(World.NETHER));
				assertTrue(option.outsideIntendedDimension(World.OVERWORLD));
				continue;
			}
			assertNull(option.intendedDimension(), option.id());
			assertFalse(option.outsideIntendedDimension(World.OVERWORLD), option.id());
			assertFalse(option.outsideIntendedDimension(World.NETHER), option.id());
			assertFalse(option.outsideIntendedDimension(World.END), option.id());
		}
	}

	@Test
	void aDeclaredDimensionWarnsOnlyElsewhere() {
		FarmOption gold = FarmOptions.CROP.intendedIn(World.NETHER);
		assertTrue(gold.outsideIntendedDimension(World.OVERWORLD));
		assertTrue(gold.outsideIntendedDimension(World.END));
		assertFalse(gold.outsideIntendedDimension(World.NETHER));
	}

	@Test
	void declaringADimensionKeepsTheRestOfTheOption() {
		FarmOption gold = FarmOptions.CROP.intendedIn(World.NETHER);
		assertEquals(FarmOptions.CROP.id(), gold.id());
		assertEquals(FarmOptions.CROP.icon(), gold.icon());
		assertEquals(FarmOptions.CROP.translationKey(), gold.translationKey());
		assertNull(FarmOptions.CROP.intendedDimension());
	}

	@Test
	void importedSchematicsNeverDeclareOne() {
		FarmOption imported = new FarmOption("nbt:one", FarmOptions.CROP.icon(), "farm.the_dream_table.imported", "One");
		assertNull(imported.intendedDimension());
		assertFalse(imported.outsideIntendedDimension(World.NETHER));
	}
}
