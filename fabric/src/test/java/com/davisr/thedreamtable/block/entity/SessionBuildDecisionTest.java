package com.davisr.thedreamtable.block.entity;

import com.davisr.thedreamtable.block.entity.SessionBuild.ContinueDecision;
import com.davisr.thedreamtable.block.entity.SessionBuild.RepairCheck;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.davisr.thedreamtable.block.entity.DreamTableBlockEntity.PHASE_BLOCKED;
import static com.davisr.thedreamtable.block.entity.DreamTableBlockEntity.PHASE_BUILDING;
import static com.davisr.thedreamtable.block.entity.DreamTableBlockEntity.PHASE_DONE;
import static com.davisr.thedreamtable.block.entity.DreamTableBlockEntity.PHASE_IDLE;
import static com.davisr.thedreamtable.block.entity.DreamTableBlockEntity.PHASE_NEED;
import static com.davisr.thedreamtable.block.entity.DreamTableBlockEntity.PHASE_PAUSED;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** The pure decisions behind a build in motion: whether it may go on, and whether a repair still holds its right to. */
class SessionBuildDecisionTest {
	private static final List<String> LEASE_PHASES = List.of(PHASE_BUILDING, PHASE_PAUSED, PHASE_BLOCKED, PHASE_NEED);

	@Test
	void aBuildGoesOnOnlyWhileItsFarmExistsAndMatches() {
		assertEquals(ContinueDecision.CONTINUE, SessionBuild.continueDecision(true, true));
		assertEquals(ContinueDecision.STOP, SessionBuild.continueDecision(true, false));
		assertEquals(ContinueDecision.STOP, SessionBuild.continueDecision(false, true));
		assertEquals(ContinueDecision.STOP, SessionBuild.continueDecision(false, false));
	}

	@Test
	void everyPhaseThatHoldsALeaseIsChecked() {
		for (String phase : LEASE_PHASES) {
			assertEquals(RepairCheck.KEEP, SessionBuild.repairCheck(phase, true, true, true), phase);
			assertEquals(RepairCheck.END_REVOKED, SessionBuild.repairCheck(phase, true, true, false), phase);
			assertEquals(RepairCheck.END_LOST, SessionBuild.repairCheck(phase, true, false, true), phase);
			// A lost lease is the stronger finding: there is nothing left to hold, whatever the role.
			assertEquals(RepairCheck.END_LOST, SessionBuild.repairCheck(phase, true, false, false), phase);
		}
	}

	@Test
	void aRevokedOperatorsStoppedRepairIsEndedSoTheOwnerIsNotStuck() {
		assertEquals(RepairCheck.END_REVOKED, SessionBuild.repairCheck(PHASE_PAUSED, true, true, false));
		assertEquals(RepairCheck.END_REVOKED, SessionBuild.repairCheck(PHASE_BLOCKED, true, true, false));
		assertEquals(RepairCheck.END_REVOKED, SessionBuild.repairCheck(PHASE_NEED, true, true, false));
	}

	@Test
	void sessionsWithoutARepairOrWithoutALeaseAreLeftAlone() {
		for (String phase : List.of(PHASE_IDLE, PHASE_DONE)) {
			assertEquals(RepairCheck.NONE, SessionBuild.repairCheck(phase, true, false, false), phase);
		}
		for (String phase : LEASE_PHASES) {
			assertEquals(RepairCheck.NONE, SessionBuild.repairCheck(phase, false, false, false), phase);
		}
	}
}
