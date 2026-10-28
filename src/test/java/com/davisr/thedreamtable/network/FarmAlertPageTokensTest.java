package com.davisr.thedreamtable.network;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FarmAlertPageTokensTest {
	private static final UUID ALICE = UUID.fromString("10000000-0000-0000-0000-000000000001");
	private static final UUID BOB = UUID.fromString("20000000-0000-0000-0000-000000000002");

	@Test
	void acceptsOnlyReferencesIssuedInThePlayersCurrentPage() {
		FarmAlertPageTokens tokens = new FarmAlertPageTokens();
		String token = tokens.issue(ALICE, "minecraft:overworld|10000000-0000-0000-0000-000000000010", List.of(4L, 9L));

		assertTrue(tokens.accept(ALICE, token, "minecraft:overworld|10000000-0000-0000-0000-000000000010", List.of(4L)));
		assertFalse(tokens.accept(ALICE, token, "minecraft:overworld|10000000-0000-0000-0000-000000000010", List.of(5L)));
		assertFalse(tokens.accept(BOB, token, "minecraft:overworld|10000000-0000-0000-0000-000000000010", List.of(4L)));
	}

	@Test
	void rejectsTokensSupersededByANewerPage() {
		FarmAlertPageTokens tokens = new FarmAlertPageTokens();
		String old = tokens.issue(ALICE, "minecraft:overworld|10000000-0000-0000-0000-000000000010", List.of(4L));
		String current = tokens.issue(ALICE, "minecraft:overworld|10000000-0000-0000-0000-000000000010", List.of(9L));

		assertFalse(tokens.accept(ALICE, old, "minecraft:overworld|10000000-0000-0000-0000-000000000010", List.of(4L)));
		assertTrue(tokens.accept(ALICE, current, "minecraft:overworld|10000000-0000-0000-0000-000000000010", List.of(9L)));
	}
}
