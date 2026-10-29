package com.davisr.thedreamtable.client;

import com.davisr.thedreamtable.farm.FarmLoader;
import com.davisr.thedreamtable.farm.FarmRole;
import com.davisr.thedreamtable.network.TrackedFarmPayload;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrackedFarmHudTest {
	@Test
	void directionUsesTheDominantHorizontalAxis() {
		assertEquals("E", TrackedFarmHud.direction(8, 3));
		assertEquals("W", TrackedFarmHud.direction(-8, 3));
		assertEquals("S", TrackedFarmHud.direction(3, 8));
		assertEquals("N", TrackedFarmHud.direction(3, -8));
	}

	@Test
	void theLineSitsUnderATallerNoticeAndKeepsItsPlaceOtherwise() {
		assertEquals(28, TrackedFarmHud.lineY(0), "no notice");
		assertEquals(28, TrackedFarmHud.lineY(26), "a one-line notice ends above the usual place");
		assertEquals(54 + 2, TrackedFarmHud.lineY(54), "a three-line notice pushes the line down");
	}

	private static FarmLoader.Entry entry(String key, boolean tracked) {
		return new FarmLoader.Entry(key, "farm-" + key, "", "Name " + key, "minecraft:the_nether", new BlockPos(1, 2, 3), FarmRole.OWNER,
				false, tracked, false, false, 0, 0, true, -1);
	}

	@Test
	void followsTheListAndForgetsWhenTheServerIsLeft() {
		TrackedFarmHud.follow(List.of(entry("a", false), entry("b", true)));
		assertEquals(new TrackedFarmPayload("farm-b", "Name b", "minecraft:the_nether", new BlockPos(1, 2, 3)), TrackedFarmHud.tracked());

		TrackedFarmHud.follow(List.of(entry("a", false)));
		assertTrue(TrackedFarmHud.tracked().isNone());

		TrackedFarmHud.follow(List.of(entry("b", true)));
		TrackedFarmHud.clear();
		assertTrue(TrackedFarmHud.tracked().isNone());
	}
}
