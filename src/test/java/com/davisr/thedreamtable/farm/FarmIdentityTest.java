package com.davisr.thedreamtable.farm;

import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FarmIdentityTest {
	private static final UUID INSTANCE = UUID.fromString("12345678-1234-5678-9abc-123456789abc");

	@Test
	void keyRoundTripsAsDimensionAndImmutableInstanceUuid() {
		FarmKey key = new FarmKey(Identifier.of("minecraft", "overworld"), INSTANCE);

		assertEquals("minecraft:overworld|12345678-1234-5678-9abc-123456789abc", key.externalForm());
		assertEquals(key, FarmKey.parse(key.externalForm()));
	}

	@Test
	void parseRejectsMissingExtraAndInvalidParts() {
		assertThrows(IllegalArgumentException.class, () -> FarmKey.parse("minecraft:overworld"));
		assertThrows(IllegalArgumentException.class, () -> FarmKey.parse("minecraft:overworld|" + INSTANCE + "|extra"));
		assertThrows(IllegalArgumentException.class, () -> FarmKey.parse("Not A Dimension|" + INSTANCE));
		assertThrows(IllegalArgumentException.class, () -> FarmKey.parse("minecraft:overworld|not-a-uuid"));
	}
}
