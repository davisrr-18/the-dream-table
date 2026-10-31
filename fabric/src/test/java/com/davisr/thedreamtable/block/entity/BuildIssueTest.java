package com.davisr.thedreamtable.block.entity;

import com.davisr.thedreamtable.GameSetup;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildIssueTest {
	@BeforeAll
	static void setUp() {
		GameSetup.bootstrap();
	}

	@Test
	void survivesSaving() {
		BuildIssue issue = BuildIssue.of(BuildIssue.Kind.OBSTACLE, new BlockPos(10, 64, -3), Blocks.CHEST).again(2);
		NbtCompound nbt = new NbtCompound();
		issue.write(nbt);
		assertEquals(issue, BuildIssue.read(nbt));
	}

	@Test
	void missingOrUnknownIssueReadsAsNone() {
		assertNull(BuildIssue.read(new NbtCompound()));
		NbtCompound nbt = new NbtCompound();
		BuildIssue.at(BuildIssue.Kind.PROTECTED, BlockPos.ORIGIN).write(nbt);
		nbt.getCompound("Issue").putString("Kind", "SOMETHING_NEW");
		assertNull(BuildIssue.read(nbt));
	}

	@Test
	void onlyCausesThatCanClearByThemselvesAreTemporary() {
		assertTrue(BuildIssue.of(BuildIssue.Kind.PLACE_FAILED, BlockPos.ORIGIN, Blocks.HOPPER).temporary());
		assertTrue(BuildIssue.at(BuildIssue.Kind.UNLOADED, BlockPos.ORIGIN).temporary());
		assertFalse(BuildIssue.of(BuildIssue.Kind.OBSTACLE, BlockPos.ORIGIN, Blocks.CHEST).temporary());
		assertFalse(BuildIssue.at(BuildIssue.Kind.PROTECTED, BlockPos.ORIGIN).temporary());
		assertFalse(BuildIssue.of(BuildIssue.Kind.MATERIAL, BlockPos.ORIGIN, Items.HOPPER).temporary());
	}

	@Test
	void aDreamTableInTheWaySurvivesSavingAndNeedsThePlayer() {
		BuildIssue issue = BuildIssue.at(BuildIssue.Kind.TABLE_IN_WAY, new BlockPos(4, 70, -2));
		NbtCompound nbt = new NbtCompound();
		issue.write(nbt);
		assertEquals(issue, BuildIssue.read(nbt));
		assertFalse(issue.temporary());
		assertEquals("gui.the_dream_table.issue.table_in_way.fix", ((TranslatableTextContent) issue.fix().getContent()).getKey());
	}

	@Test
	void aFailureThatRepeatsNeedsThePlayer() {
		BuildIssue failed = BuildIssue.of(BuildIssue.Kind.PLACE_FAILED, BlockPos.ORIGIN, Blocks.HOPPER);
		assertFalse(failed.again(BuildIssue.REPEATED).temporary());
		assertEquals("gui.the_dream_table.issue.place_failed.repeated", ((TranslatableTextContent) failed.again(BuildIssue.REPEATED).fix().getContent()).getKey());
	}

	@Test
	void entityBlockageSurvivesSavingAndNeedsThePlayer() {
		BuildIssue issue = BuildIssue.at(BuildIssue.Kind.ENTITY_BLOCKED, new BlockPos(4, 70, -2));
		NbtCompound nbt = new NbtCompound();

		issue.write(nbt);

		assertEquals(issue, BuildIssue.read(nbt));
		assertFalse(issue.temporary());
		assertEquals("gui.the_dream_table.issue.entity_blocked", ((TranslatableTextContent) issue.what().getContent()).getKey());
		assertEquals("gui.the_dream_table.issue.entity_blocked.fix", ((TranslatableTextContent) issue.fix().getContent()).getKey());
	}

	@Test
	void aChangedFarmNeedsThePlayerAndSurvivesSaving() {
		BuildIssue issue = BuildIssue.at(BuildIssue.Kind.FARM_CHANGED, new BlockPos(4, 70, -2));
		NbtCompound nbt = new NbtCompound();

		issue.write(nbt);

		assertEquals(issue, BuildIssue.read(nbt));
		assertFalse(issue.temporary());
		assertEquals("gui.the_dream_table.issue.farm_changed", ((TranslatableTextContent) issue.what().getContent()).getKey());
		assertEquals("gui.the_dream_table.issue.farm_changed.fix", ((TranslatableTextContent) issue.fix().getContent()).getKey());
	}
}
