package com.davisr.thedreamtable.fabric;

import com.davisr.thedreamtable.GameSetup;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Fabric mixins behind "Protect My Farms" against explosions and endermen actually apply to the game's classes. */
class GriefMixinsTest {
	@BeforeAll
	static void bootstrap() {
		GameSetup.bootstrap();
	}

	private static boolean hasInjected(String className, String methodPrefix) throws ClassNotFoundException {
		return Arrays.stream(Class.forName(className).getDeclaredMethods()).anyMatch(method -> method.getName().contains(methodPrefix));
	}

	@Test
	void theExplosionMixinIsApplied() throws ClassNotFoundException {
		assertTrue(hasInjected("net.minecraft.world.explosion.ExplosionImpl","thedreamtable$filterBlocks"));
	}

	@Test
	void theEndermanPickUpMixinIsApplied() throws ClassNotFoundException {
		assertTrue(hasInjected("net.minecraft.entity.mob.EndermanEntity$PickUpBlockGoal", "thedreamtable$guardFarms"));
	}
}
