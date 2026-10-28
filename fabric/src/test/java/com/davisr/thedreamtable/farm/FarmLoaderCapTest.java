package com.davisr.thedreamtable.farm;

import com.davisr.thedreamtable.GameSetup;
import com.davisr.thedreamtable.farm.schematic.SchematicReader;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Keep Loaded size cap, and the renewal request that stops a farm's random ticks at once. */
class FarmLoaderCapTest {
	private static final String[] FILES = {"sugar_cane_farm", "bamboo_farm", "cactus_farm", "crop_farm", "honey_farm", "mob_farm", "iron_farm"};

	@BeforeAll
	static void setUp() {
		GameSetup.bootstrap();
	}

	@Test
	void countsEveryChunkTheBoxTouches() {
		assertEquals(1, FarmLoader.chunkCount(new Box(0, 64, 0, 16, 70, 16)));
		assertEquals(4, FarmLoader.chunkCount(new Box(15, 64, 15, 17, 70, 17)));
		assertEquals(2, FarmLoader.chunkCount(new Box(0, 64, 0, 17, 70, 1)));
	}

	@Test
	void countsChunksAcrossTheOriginAndForNegativeCoordinates() {
		assertEquals(4, FarmLoader.chunkCount(new Box(-1, 0, -1, 1, 5, 1)));
		assertEquals(1, FarmLoader.chunkCount(new Box(-16, 0, -16, 0, 5, 0)));
		assertEquals(2, FarmLoader.chunkCount(new Box(-17, 0, -16, -15, 5, 0)));
	}

	@Test
	void theCapAcceptsTheLimitAndRefusesOneChunkMore() {
		int side = 8;
		assertEquals(FarmLoader.MAX_CHUNKS_PER_FARM, side * side);
		assertTrue(FarmLoader.withinChunkCap(new Box(0, 0, 0, side * 16, 10, side * 16)));
		assertFalse(FarmLoader.withinChunkCap(new Box(0, 0, 0, side * 16 + 1, 10, side * 16)));
	}

	@Test
	void aSparseFarmWithFewBlocksButAHugeSpanIsRefused() {
		assertFalse(FarmLoader.withinChunkCap(new Box(0, 64, 0, 2000, 65, 2000)));
		assertFalse(FarmLoader.withinChunkCap(new Box(-30_000_000, 0, -30_000_000, 30_000_000, 1, 30_000_000)));
	}

	@Test
	void everyBundledFarmFitsAtAnyAlignmentAndFacingWithMargin() throws IOException {
		long largest = 0;
		for (String file : FILES) {
			FarmTemplate farm;
			try (InputStream stream = FarmLoaderCapTest.class.getResourceAsStream("/data/the_dream_table/structures/" + file + ".schem")) {
				assertNotNull(stream, file + " is missing");
				farm = SchematicReader.read(file, stream);
			}
			for (Direction facing : List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST)) {
				for (int offset = 0; offset < 16; offset++) {
					Box box = new FarmSite(new BlockPos(offset - 100, 64, offset + 37), new BlockPos(offset, 64, 15 - offset), facing).footprint(farm);
					assertTrue(FarmLoader.withinChunkCap(box), file + " " + facing + " " + offset);
					largest = Math.max(largest, FarmLoader.chunkCount(box));
				}
			}
		}
		assertTrue(largest * 2 <= FarmLoader.MAX_CHUNKS_PER_FARM, "the cap should leave at least double the largest built-in farm: " + largest);
	}

	@Test
	void aRenewalIsDueEveryIntervalOrWhenRequested() {
		assertTrue(FarmLoader.renewalDue(0, false));
		assertTrue(FarmLoader.renewalDue(200, false));
		assertFalse(FarmLoader.renewalDue(201, false));
		assertFalse(FarmLoader.renewalDue(399, false));
		assertTrue(FarmLoader.renewalDue(201, true));
	}

	@Test
	void unpickingARequestsOneImmediateRenewalButPickingDoesNot() {
		FarmLoader.consumeRenewalRequest();
		FarmLoader loader = new FarmLoader();
		UUID owner = UUID.randomUUID();

		assertEquals(FarmLoader.ToggleResult.SELECTED, loader.toggleKnown(owner, "farm", true, FarmRole.OWNER));
		assertFalse(FarmLoader.consumeRenewalRequest());

		assertEquals(FarmLoader.ToggleResult.UNSELECTED, loader.toggleKnown(owner, "farm", true, FarmRole.OWNER));
		assertTrue(FarmLoader.consumeRenewalRequest());
		assertFalse(FarmLoader.consumeRenewalRequest(), "the request is consumed once");
	}
}
