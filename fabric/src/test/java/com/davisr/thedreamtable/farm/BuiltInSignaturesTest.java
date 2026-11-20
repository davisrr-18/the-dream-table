package com.davisr.thedreamtable.farm;

import com.davisr.thedreamtable.GameSetup;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A build and a take-down job remember the farm's fingerprint, and a different one means "the farm changed" (the project
 * is not resumed, a take-down job is dropped). The fingerprints in {@code fixtures/1.21.1/farm_signatures.properties} were
 * printed by the 1.21.1 build of the mod, so a world saved there with a build in progress must still match on this game,
 * even though the game renamed {@code minecraft:chain} to {@code minecraft:iron_chain} on the way.
 */
class BuiltInSignaturesTest {
	@BeforeAll
	static void setUp() {
		GameSetup.bootstrap();
	}

	@Test
	void everyBuiltInFarmKeepsItsFingerprintFrom121() throws IOException {
		Properties saved = new Properties();
		try (InputStream stream = BuiltInSignaturesTest.class.getResourceAsStream("/fixtures/1.21.1/farm_signatures.properties")) {
			assertNotNull(stream, "farm_signatures.properties is missing");
			saved.load(stream);
		}
		assertEquals(FarmOptions.ALL.size(), saved.size(), "a built-in farm has no saved fingerprint, or one is left over");
		List<String> changed = new ArrayList<>();
		for (FarmOptions.FarmOption option : FarmOptions.ALL) {
			FarmTemplate template = FarmTemplates.get(option.id());
			assertNotNull(template, option.id());
			assertTrue(saved.containsKey(option.id()), option.id());
			long expected = Long.parseLong(saved.getProperty(option.id()));
			if (expected != template.signature()) {
				changed.add(option.id() + " (1.21.1 " + expected + ", now " + template.signature() + ")");
			}
		}
		assertEquals(List.of(), changed, "farms whose fingerprint differs from the one 1.21.1 saved");
	}
}
