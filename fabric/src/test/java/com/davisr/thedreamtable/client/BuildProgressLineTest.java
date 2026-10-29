package com.davisr.thedreamtable.client;

import com.davisr.thedreamtable.GameSetup;
import com.davisr.thedreamtable.block.entity.DreamTableBlockEntity;
import com.davisr.thedreamtable.client.BuildProgressLine.Snapshot;
import com.davisr.thedreamtable.client.BuildProgressLine.Step;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class BuildProgressLineTest {
	@BeforeAll
	static void bootstrap() {
		GameSetup.bootstrap();
	}

	private static Snapshot building(int index) {
		return new Snapshot(true, true, true, DreamTableBlockEntity.PHASE_BUILDING, index, 100, 10);
	}

	private static TranslatableTextContent content(Object text) {
		return assertInstanceOf(TranslatableTextContent.class, assertInstanceOf(Text.class, text).getContent());
	}

	@Test
	void aRunningBuildInALoadedChunkShowsTheLine() {
		assertEquals(Step.SHOW, BuildProgressLine.step(building(0)));
		assertEquals(Step.SHOW, BuildProgressLine.step(building(57)));
	}

	@Test
	void leavingTheChunkHidesTheLineButKeepsWatching() {
		assertEquals(Step.HIDE, BuildProgressLine.step(new Snapshot(false, false, true, DreamTableBlockEntity.PHASE_BUILDING, 5, 100, 10)));
	}

	@Test
	void aTableWithoutTheSessionYetHidesTheLineButKeepsWatching() {
		assertEquals(Step.HIDE, BuildProgressLine.step(new Snapshot(true, true, false, "", 0, 0, 0)));
	}

	@Test
	void everyOtherPhaseEndsTheLine() {
		for (String phase : new String[]{DreamTableBlockEntity.PHASE_PAUSED, DreamTableBlockEntity.PHASE_BLOCKED, DreamTableBlockEntity.PHASE_NEED,
				DreamTableBlockEntity.PHASE_DONE, DreamTableBlockEntity.PHASE_IDLE}) {
			assertEquals(Step.FORGET, BuildProgressLine.step(new Snapshot(true, true, true, phase, 5, 100, 10)), phase);
		}
	}

	@Test
	void aBrokenTableEndsTheLine() {
		assertEquals(Step.FORGET, BuildProgressLine.step(new Snapshot(true, false, true, DreamTableBlockEntity.PHASE_BUILDING, 5, 100, 10)));
	}

	@Test
	void aFarmWithoutKnownStepsShowsNothing() {
		assertEquals(Step.HIDE, BuildProgressLine.step(new Snapshot(true, true, true, DreamTableBlockEntity.PHASE_BUILDING, 0, 0, 10)));
	}

	@Test
	void theTextFollowsThePlacedBlocksAndAddsTheTimeLeftWhenTheRateIsKnown() {
		TranslatableTextContent early = content(BuildProgressLine.text(5, 100, 10));
		TranslatableTextContent later = content(BuildProgressLine.text(60, 100, 10));

		assertEquals("gui.the_dream_table.with_eta", early.getKey());
		TranslatableTextContent earlyStatus = content(early.getArgs()[0]);
		TranslatableTextContent laterStatus = content(later.getArgs()[0]);
		assertEquals("gui.the_dream_table.building", earlyStatus.getKey());
		assertEquals(5, earlyStatus.getArgs()[0]);
		assertEquals(60, laterStatus.getArgs()[0]);
		assertEquals(100, laterStatus.getArgs()[1]);
	}

	@Test
	void withoutARateTheTextIsPlainProgress() {
		TranslatableTextContent text = content(BuildProgressLine.text(12, 340, 0));

		assertEquals("gui.the_dream_table.building", text.getKey());
		assertEquals(12, text.getArgs()[0]);
		assertEquals(340, text.getArgs()[1]);
	}
}
