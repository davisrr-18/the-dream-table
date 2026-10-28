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
	void aFarmTheServerNoLongerListsIsDropped() {
		list(7L);
		FarmTemplates.putRemote(template());

		FarmTemplates.useRemote(List.of(), Map.of(), Map.of(), Map.of(), List.of());

		assertNull(FarmTemplates.get(ID));
	}
}
