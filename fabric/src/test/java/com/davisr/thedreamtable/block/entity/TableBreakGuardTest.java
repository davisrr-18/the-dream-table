package com.davisr.thedreamtable.block.entity;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static com.davisr.thedreamtable.block.entity.TableBreakGuard.Claim;
import static com.davisr.thedreamtable.block.entity.TableBreakGuard.Reason.BUILD;
import static com.davisr.thedreamtable.block.entity.TableBreakGuard.Reason.MATERIALS;
import static com.davisr.thedreamtable.block.entity.TableBreakGuard.Reason.TAKE_DOWN;
import static com.davisr.thedreamtable.block.entity.TableBreakGuard.Reason.UNREADABLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Who may break a table that holds other players' materials, builds or Take Downs, their own builds, or saved data this version can't read. */
class TableBreakGuardTest {
	private static final UUID ME = UUID.fromString("10000000-0000-0000-0000-000000000001");
	private static final UUID OTHER = UUID.fromString("20000000-0000-0000-0000-000000000002");
	private static final UUID THIRD = UUID.fromString("30000000-0000-0000-0000-000000000003");

	@Test
	void anEmptyTableBreaksForAnyone() {
		assertTrue(TableBreakGuard.blocker(List.of(), ME, false).isEmpty());
	}

	@Test
	void theBreakersOwnMaterialsAndTakeDownNeverStopThem() {
		assertTrue(TableBreakGuard.blocker(List.of(new Claim(ME, MATERIALS), new Claim(ME, TAKE_DOWN)), ME, false).isEmpty());
	}

	@Test
	void theBreakersOwnRunningBuildStopsThemToo() {
		Claim blocker = TableBreakGuard.blocker(List.of(new Claim(ME, BUILD)), ME, false).orElseThrow();
		assertEquals(BUILD, blocker.reason());
		assertEquals(ME, blocker.player());
	}

	@Test
	void anotherPlayersMaterialsBuildOrTakeDownStopTheBreak() {
		assertEquals(MATERIALS, TableBreakGuard.blocker(List.of(new Claim(OTHER, MATERIALS)), ME, false).orElseThrow().reason());
		assertEquals(BUILD, TableBreakGuard.blocker(List.of(new Claim(OTHER, BUILD)), ME, false).orElseThrow().reason());
		assertEquals(TAKE_DOWN, TableBreakGuard.blocker(List.of(new Claim(OTHER, TAKE_DOWN)), ME, false).orElseThrow().reason());
	}

	@Test
	void anotherPlayersMaterialsStopTheOwnerWhoHasMaterialsToo() {
		List<Claim> mixed = List.of(new Claim(ME, MATERIALS), new Claim(OTHER, MATERIALS));
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
	void unreadableDataStopsTheOwnerToo() {
		Claim blocker = TableBreakGuard.blocker(List.of(new Claim(ME, UNREADABLE)), ME, false).orElseThrow();
		assertEquals(UNREADABLE, blocker.reason());
		assertEquals(ME, blocker.player());
	}

	@Test
	void unreadableDataWithoutANamedPlayerStopsEveryone() {
		List<Claim> claims = List.of(new Claim(null, UNREADABLE));
		assertEquals(UNREADABLE, TableBreakGuard.blocker(claims, ME, false).orElseThrow().reason());
		assertEquals(UNREADABLE, TableBreakGuard.blocker(claims, OTHER, false).orElseThrow().reason());
	}

	@Test
	void unreadableDataIsReportedBeforeOtherClaims() {
		List<Claim> claims = List.of(new Claim(OTHER, TAKE_DOWN), new Claim(ME, UNREADABLE), new Claim(THIRD, BUILD));
		assertEquals(UNREADABLE, TableBreakGuard.blocker(claims, ME, false).orElseThrow().reason());
	}

	@Test
	void serverOperatorsAlwaysMayBreak() {
		assertTrue(TableBreakGuard.blocker(List.of(new Claim(OTHER, BUILD), new Claim(THIRD, TAKE_DOWN)), ME, true).isEmpty());
		assertTrue(TableBreakGuard.blocker(List.of(new Claim(ME, BUILD), new Claim(null, UNREADABLE)), ME, true).isEmpty());
	}

	@Test
	void everyReasonHasItsOwnMessage() {
		assertEquals(4, Arrays.stream(TableBreakGuard.Reason.values()).map(TableBreakGuard.Reason::key).distinct().count());
	}

	@Test
	void theOwnBuildHasItsOwnMessage() {
		for (TableBreakGuard.Reason reason : TableBreakGuard.Reason.values()) {
			assertNotEquals(TableBreakGuard.OWN_BUILD_KEY, reason.key());
		}
	}
}
