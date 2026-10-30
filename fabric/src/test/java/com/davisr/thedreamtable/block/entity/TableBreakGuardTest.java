package com.davisr.thedreamtable.block.entity;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static com.davisr.thedreamtable.block.entity.TableBreakGuard.Claim;
import static com.davisr.thedreamtable.block.entity.TableBreakGuard.Reason.BUILD;
import static com.davisr.thedreamtable.block.entity.TableBreakGuard.Reason.MATERIALS;
import static com.davisr.thedreamtable.block.entity.TableBreakGuard.Reason.TAKE_DOWN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Who may break a table that holds other players' materials, builds or Take Downs. */
class TableBreakGuardTest {
	private static final UUID ME = UUID.fromString("10000000-0000-0000-0000-000000000001");
	private static final UUID OTHER = UUID.fromString("20000000-0000-0000-0000-000000000002");
	private static final UUID THIRD = UUID.fromString("30000000-0000-0000-0000-000000000003");

	@Test
	void anEmptyTableBreaksForAnyone() {
		assertTrue(TableBreakGuard.blocker(List.of(), ME, false).isEmpty());
	}

	@Test
	void theBreakersOwnSessionsNeverStopThem() {
		assertTrue(TableBreakGuard.blocker(List.of(new Claim(ME, BUILD), new Claim(ME, MATERIALS)), ME, false).isEmpty());
	}

	@Test
	void anotherPlayersMaterialsBuildOrTakeDownStopTheBreak() {
		assertEquals(MATERIALS, TableBreakGuard.blocker(List.of(new Claim(OTHER, MATERIALS)), ME, false).orElseThrow().reason());
		assertEquals(BUILD, TableBreakGuard.blocker(List.of(new Claim(OTHER, BUILD)), ME, false).orElseThrow().reason());
		assertEquals(TAKE_DOWN, TableBreakGuard.blocker(List.of(new Claim(OTHER, TAKE_DOWN)), ME, false).orElseThrow().reason());
	}

	@Test
	void ownerMustOwnEverySessionAtTheTable() {
		List<Claim> mixed = List.of(new Claim(ME, BUILD), new Claim(OTHER, MATERIALS));
		assertEquals(OTHER, TableBreakGuard.blocker(mixed, ME, false).orElseThrow().player());
	}

	@Test
	void theClearestReasonIsReportedWhenSeveralPlayersAreAtStake() {
		List<Claim> claims = List.of(new Claim(OTHER, MATERIALS), new Claim(THIRD, TAKE_DOWN), new Claim(OTHER, BUILD));
		Claim blocker = TableBreakGuard.blocker(claims, ME, false).orElseThrow();
		assertEquals(THIRD, blocker.player());
		assertEquals(TAKE_DOWN, blocker.reason());
	}

	@Test
	void serverOperatorsAlwaysMayBreak() {
		assertTrue(TableBreakGuard.blocker(List.of(new Claim(OTHER, BUILD), new Claim(THIRD, TAKE_DOWN)), ME, true).isEmpty());
	}

	@Test
	void everyReasonHasItsOwnMessage() {
		assertEquals(3, java.util.Arrays.stream(TableBreakGuard.Reason.values()).map(TableBreakGuard.Reason::key).distinct().count());
	}
}
