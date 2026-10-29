package com.davisr.thedreamtable.farm;

import com.davisr.thedreamtable.GameSetup;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/** A client holding a server's schematic must stop using it when the server's file changes. */
class FarmTemplatesRemoteTest {
	private static final String ID = "nbt:remote_test";
	private final List<String> asked = new ArrayList<>();

	@BeforeAll
	static void setUp() {
		GameSetup.bootstrap();
	}

	@AfterEach
	void restore() {
		FarmTemplates.useLocal();
		FarmTemplates.onMissing(id -> {
		});
	}

	private static FarmTemplate template() {
		return FarmTemplate.of(ID, List.of(FarmStep.block(0, 0, 0, Blocks.STONE.getDefaultState(), Items.STONE, null)));
	}

	private void list(long version) {
		FarmTemplates.useRemote(List.of(), Map.of(ID, 1), Map.of(ID, version), Map.of(), List.of());
	}

	@Test
	void aFarmIsKeptWhileTheServersFileKeepsItsVersion() {
		FarmTemplate fetched = template();
		list(7L);
		FarmTemplates.putRemote(fetched);

		list(7L);

		assertSame(fetched, FarmTemplates.get(ID));
	}

	@Test
	void anEditedFileDropsTheOldCopyAndAsksForTheNewOne() {
		FarmTemplates.onMissing(asked::add);
		list(7L);
		FarmTemplates.putRemote(template());

		list(8L);

		assertNull(FarmTemplates.get(ID));
		assertEquals(List.of(ID), asked);
	}

	@Test
	void aReplyForAnOlderVersionThanTheListIsNotStoredAsCurrent() {
		list(8L);

		assertFalse(FarmTemplates.putRemote(template(), 7L));
		assertNull(FarmTemplates.get(ID));
	}

	@Test
	void aFileEditedAfterTheListIsTakenOnTheSecondMatchingReply() {
		FarmTemplate fetched = template();
		list(7L);

		assertFalse(FarmTemplates.putRemote(template(), 8L), "could still be an answer from before the list");
		assertTrue(FarmTemplates.putRemote(fetched, 8L), "the server answered 8 twice: the list is the stale side");
		assertSame(fetched, FarmTemplates.get(ID));
		assertFalse(FarmTemplates.putRemote(template(), 7L), "the older file no longer replaces it");
		assertSame(fetched, FarmTemplates.get(ID));
	}

	@Test
	void aReplyForTheListedVersionIsStored() {
		FarmTemplate fetched = template();
		list(8L);

		assertTrue(FarmTemplates.putRemote(fetched, 8L));
		assertSame(fetched, FarmTemplates.get(ID));
	}

	@Test
	void theStaleReplyIsDroppedWhenTheNewListArrivesAfterTheRequest() {
		FarmTemplates.onMissing(asked::add);
		list(7L);
		assertNull(FarmTemplates.get(ID));
		assertEquals(List.of(ID), asked);
		// The list announces a newer file before the answer to the version-7 request lands.
		list(8L);

		assertFalse(FarmTemplates.putRemote(template(), 7L));
		assertNull(FarmTemplates.get(ID));
		assertEquals(List.of(ID, ID), asked, "still missing, so it is asked for again");
	}

	@Test
	void aSavedDesignTheListDoesNotVersionIsAlwaysKept() {
		FarmTemplates.useRemote(List.of(), Map.of(), Map.of(), Map.of(), List.of());
		FarmTemplate saved = FarmTemplate.of("saved:x#1", List.of(FarmStep.block(0, 0, 0, Blocks.STONE.getDefaultState(), Items.STONE, null)));

		assertTrue(FarmTemplates.putRemote(saved, 0L));
		assertSame(saved, FarmTemplates.get("saved:x#1"));
	}

	@Test
	void nothingIsStoredWhilePlayingOnThisComputersFiles() {
		assertFalse(FarmTemplates.putRemote(template(), 0L));
	}

	@Test
	void aFarmTheServerNoLongerListsIsDropped() {
		list(7L);
		FarmTemplates.putRemote(template());

		FarmTemplates.useRemote(List.of(), Map.of(), Map.of(), Map.of(), List.of());

		assertNull(FarmTemplates.get(ID));
	}
}
