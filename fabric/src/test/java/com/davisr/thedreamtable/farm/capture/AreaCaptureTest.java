package com.davisr.thedreamtable.farm.capture;

import com.davisr.thedreamtable.GameSetup;
import com.davisr.thedreamtable.block.DreamTableBlock;
import com.davisr.thedreamtable.farm.FarmStep;
import com.davisr.thedreamtable.farm.ImportNote;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import com.davisr.thedreamtable.farm.schematic.SchematicReader;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The whole capture path on a fake world, read back through the real importer. */
class AreaCaptureTest {
	private static final int DATA_VERSION = 3955;
	private static final int BOTTOM = -64;
	private static final int TOP = 320;

	@TempDir
	Path folder;

	private final Map<BlockPos, BlockState> world = new HashMap<>();
	private final AtomicInteger reads = new AtomicInteger();

	@BeforeAll
	static void bootstrap() {
		GameSetup.bootstrap();
	}

	private Function<BlockPos, BlockState> states() {
		return pos -> {
			reads.incrementAndGet();
			return world.getOrDefault(pos, Blocks.AIR.getDefaultState());
		};
	}

	private CaptureFiles.Saved capture(CaptureBox box, String name) {
		return AreaCapture.run(box, name, BOTTOM, TOP, DATA_VERSION, (chunkX, chunkZ) -> states(), folder, List.of());
	}

	@Test
	void everyChunkIsReadThroughItsOwnLookupAndOneUnreadyChunkStopsTheRead() {
		CaptureBox box = CaptureBox.of(new BlockPos(14, 64, 0), new BlockPos(17, 64, 0));
		CaptureScan.Result both = CaptureScan.read(box, (chunkX, chunkZ) -> pos -> chunkX == 0 ? Blocks.STONE.getDefaultState() : Blocks.DIRT.getDefaultState());
		assertNull(both.problem());
		assertEquals(List.of(Blocks.STONE, Blocks.STONE, Blocks.DIRT, Blocks.DIRT), both.blocks().stream().map(entry -> entry.state().getBlock()).toList());
		AtomicInteger asked = new AtomicInteger();
		CaptureScan.Result notReady = CaptureScan.read(box, (chunkX, chunkZ) -> chunkX == 0 ? pos -> {
			asked.incrementAndGet();
			return Blocks.STONE.getDefaultState();
		} : null);
		assertEquals(CaptureProblem.Kind.UNLOADED, notReady.problem().kind());
		assertEquals(0, asked.get());
	}

	@Test
	void anAreaTheImporterWouldRejectIsRefusedWithItsOwnReasonAndWritesNothing() throws IOException {
		String[] kinds = {"wool", "concrete", "terracotta", "stained_glass"};
		String[] colors = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};
		int x = 0;
		for (String kind : kinds) {
			for (String color : colors) {
				world.put(new BlockPos(x++, 64, 0), Registries.BLOCK.get(Identifier.of("minecraft", color + "_" + kind)).getDefaultState());
			}
		}
		CaptureFiles.Saved saved = capture(CaptureBox.of(new BlockPos(0, 64, 0), new BlockPos(x - 1, 64, 0)), "colours");
		assertEquals(CaptureProblem.Kind.REJECTED, saved.problem().kind());
		assertEquals(ImportNote.Kind.TOO_MANY_MATERIALS, saved.problem().note().kind());
		try (var files = Files.list(folder)) {
			assertEquals(0, files.count());
		}
	}

	@Test
	void aNameTakenInAnotherImporterFolderIsRefusedBeforeWriting() throws IOException {
		Path other = Files.createDirectories(folder.resolve("elsewhere"));
		Files.writeString(other.resolve("taken.nbt"), "theirs");
		world.put(new BlockPos(0, 64, 0), Blocks.STONE.getDefaultState());
		CaptureFiles.Saved saved = AreaCapture.run(CaptureBox.of(new BlockPos(0, 64, 0), new BlockPos(0, 64, 0)), "Taken", BOTTOM, TOP, DATA_VERSION,
				(chunkX, chunkZ) -> states(), folder.resolve("own"), List.of(other));
		assertEquals(CaptureProblem.Kind.EXISTS, saved.problem().kind());
		assertFalse(Files.exists(folder.resolve("own").resolve("taken.nbt")));
	}

	private void fill(CaptureBox box, BlockState state) {
		for (BlockPos pos : BlockPos.iterate(box.min(), box.max())) {
			world.put(pos.toImmutable(), state);
		}
	}

	@Test
	void theFileReadsBackThroughTheImporterWithStatesPositionsAndSize() throws IOException {
		BlockState stairs = Blocks.OAK_STAIRS.getDefaultState().with(Properties.HORIZONTAL_FACING, net.minecraft.util.math.Direction.EAST);
		BlockState table = new DreamTableBlock(AbstractBlock.Settings.create()).getDefaultState();
		world.put(new BlockPos(100, 64, 200), Blocks.STONE.getDefaultState());
		world.put(new BlockPos(101, 64, 200), stairs);
		world.put(new BlockPos(102, 64, 200), table);
		world.put(new BlockPos(103, 64, 200), Blocks.WATER.getDefaultState().with(Properties.LEVEL_15, 3));
		world.put(new BlockPos(104, 65, 202), Blocks.WATER.getDefaultState());
		world.put(new BlockPos(99, 64, 200), Blocks.DIAMOND_BLOCK.getDefaultState());
		CaptureBox box = CaptureBox.of(new BlockPos(105, 66, 202), new BlockPos(100, 64, 200));

		CaptureFiles.Saved saved = capture(box, "My Test Farm");

		assertNull(saved.problem());
		assertEquals("my_test_farm.nbt", saved.fileName());
		Path file = folder.resolve("my_test_farm.nbt");
		NbtCompound root = NbtIo.readCompressed(file, NbtSizeTracker.ofUnlimitedBytes());
		assertEquals(DATA_VERSION, root.getInt("DataVersion"));
		assertEquals(List.of(6, 3, 3), List.of(root.getList("size", 3).getInt(0), root.getList("size", 3).getInt(1), root.getList("size", 3).getInt(2)));
		assertEquals(0, root.getList("entities", 10).size());

		SchematicReader.Result result = SchematicReader.read("nbt:my_test_farm", file);
		assertNotNull(result.template());
		assertTrue(result.notes().isEmpty(), "no table-block note: the table block was left out of the file");
		Map<BlockPos, BlockState> back = new HashMap<>();
		for (FarmStep step : result.template().steps()) {
			back.put(new BlockPos(step.x(), step.y(), step.z()), step.state());
		}
		assertEquals(Map.of(
				new BlockPos(0, 0, 0), Blocks.STONE.getDefaultState(),
				new BlockPos(1, 0, 0), stairs,
				new BlockPos(4, 1, 2), Blocks.WATER.getDefaultState()), back, "air, the table block, flowing water and blocks outside the box are not written");
	}

	@Test
	void moreThanTheBlockLimitIsRefusedAndWritesNothing() throws IOException {
		CaptureBox box = CaptureBox.of(new BlockPos(0, 64, 0), new BlockPos(29, 93, 29));
		fill(box, Blocks.STONE.getDefaultState());
		CaptureFiles.Saved saved = capture(box, "big");
		assertEquals(CaptureProblem.Kind.TOO_MANY_BLOCKS, saved.problem().kind());
		try (var files = Files.list(folder)) {
			assertEquals(0, files.count());
		}
		CaptureBox exact = CaptureBox.of(new BlockPos(0, 64, 0), new BlockPos(127, 64, 127));
		fill(exact, Blocks.STONE.getDefaultState());
		assertNull(capture(exact, "exact").problem(), "exactly 16,384 blocks is the importer's limit and is allowed");
	}

	@Test
	void sizeLimitsAreTheImporters() {
		assertEquals(CaptureProblem.Kind.TOO_WIDE, CaptureBox.of(new BlockPos(0, 0, 0), new BlockPos(512, 0, 0)).sizeProblem(BOTTOM, TOP).kind());
		assertEquals(CaptureProblem.Kind.TOO_WIDE, CaptureBox.of(new BlockPos(0, 0, 0), new BlockPos(0, 0, 512)).sizeProblem(BOTTOM, TOP).kind());
		assertNull(CaptureBox.of(new BlockPos(0, 0, 0), new BlockPos(511, 0, 0)).sizeProblem(BOTTOM, TOP));
		assertEquals(CaptureProblem.Kind.TOO_WIDE, CaptureBox.of(new BlockPos(-30_000_000, 0, 0), new BlockPos(30_000_000, 0, 0)).sizeProblem(BOTTOM, TOP).kind());
		assertEquals(CaptureProblem.Kind.OUT_OF_WORLD, CaptureBox.of(new BlockPos(0, -65, 0), new BlockPos(0, 0, 0)).sizeProblem(BOTTOM, TOP).kind());
		assertEquals(CaptureProblem.Kind.OUT_OF_WORLD, CaptureBox.of(new BlockPos(0, 0, 0), new BlockPos(0, 320, 0)).sizeProblem(BOTTOM, TOP).kind());
		assertNull(CaptureBox.of(new BlockPos(0, -64, 0), new BlockPos(0, 319, 0)).sizeProblem(BOTTOM, TOP));
		assertEquals(CaptureProblem.Kind.TOO_TALL, CaptureBox.of(new BlockPos(0, -100, 0), new BlockPos(0, 300, 0)).sizeProblem(-200, 400).kind());
		assertEquals(CaptureProblem.Kind.TOO_BIG, CaptureBox.of(new BlockPos(0, 0, 0), new BlockPos(399, 9, 399)).sizeProblem(BOTTOM, TOP).kind());
	}

	@Test
	void anOversizedBoxIsNeverReadBlockByBlock() {
		capture(CaptureBox.of(new BlockPos(0, 0, 0), new BlockPos(511, 300, 511)), "huge");
		assertEquals(0, reads.get());
	}

	@Test
	void aBoxWithAnUnloadedChunkReadsNoBlock() {
		world.put(new BlockPos(0, 64, 0), Blocks.STONE.getDefaultState());
		CaptureFiles.Saved saved = AreaCapture.run(CaptureBox.of(new BlockPos(0, 64, 0), new BlockPos(40, 66, 3)), "x", BOTTOM, TOP, DATA_VERSION,
				(chunkX, chunkZ) -> chunkX < 1 ? states() : null, folder, List.of());
		assertEquals(CaptureProblem.Kind.UNLOADED, saved.problem().kind());
		assertEquals(0, reads.get(), "a block is never asked for before every chunk of the box is known to be loaded");
		assertFalse(Files.exists(folder.resolve("x.nbt")));
	}

	@Test
	void emptyBoxesAndTableOnlyBoxesAreRefused() {
		assertEquals(CaptureProblem.Kind.EMPTY, capture(CaptureBox.of(new BlockPos(0, 64, 0), new BlockPos(3, 66, 3)), "air").problem().kind());
		world.put(new BlockPos(1, 64, 1), new DreamTableBlock(AbstractBlock.Settings.create()).getDefaultState());
		assertEquals(CaptureProblem.Kind.EMPTY, capture(CaptureBox.of(new BlockPos(0, 64, 0), new BlockPos(3, 66, 3)), "table").problem().kind());
	}

	@Test
	void anUnusableNameIsRefusedBeforeAnythingIsRead() {
		world.put(new BlockPos(0, 64, 0), Blocks.STONE.getDefaultState());
		CaptureBox box = CaptureBox.of(new BlockPos(0, 64, 0), new BlockPos(0, 64, 0));
		assertEquals(CaptureProblem.Kind.BAD_NAME, capture(box, "///").problem().kind());
		assertEquals(CaptureProblem.Kind.BAD_NAME, capture(box, "CON").problem().kind());
		assertEquals(0, reads.get());
	}

	@Test
	void aSecondCaptureWithTheSameNameIsRefusedAndKeepsTheFirst() throws IOException {
		world.put(new BlockPos(0, 64, 0), Blocks.STONE.getDefaultState());
		CaptureBox box = CaptureBox.of(new BlockPos(0, 64, 0), new BlockPos(0, 64, 0));
		assertNull(capture(box, "same").problem());
		byte[] first = Files.readAllBytes(folder.resolve("same.nbt"));
		world.put(new BlockPos(0, 64, 0), Blocks.DIRT.getDefaultState());
		assertEquals(CaptureProblem.Kind.EXISTS, capture(box, "SAME").problem().kind());
		assertTrue(java.util.Arrays.equals(first, Files.readAllBytes(folder.resolve("same.nbt"))));
	}

	@Test
	void permissionIsOperatorOrHost() {
		assertTrue(CaptureAccess.allowed(true, false));
		assertTrue(CaptureAccess.allowed(false, true));
		assertTrue(CaptureAccess.allowed(true, true));
		assertFalse(CaptureAccess.allowed(false, false));
	}

	@Test
	void theTablesReachDecidesWhichBoxesCanBeCaptured() {
		BlockPos table = new BlockPos(0, 64, 0);
		assertTrue(CaptureBox.of(new BlockPos(48, 64, 0), new BlockPos(60, 70, 5)).inReachOf(table));
		assertFalse(CaptureBox.of(new BlockPos(49, 64, 0), new BlockPos(60, 70, 5)).inReachOf(table));
		assertTrue(CaptureBox.of(new BlockPos(-100, 0, -100), new BlockPos(100, 100, 100)).inReachOf(table), "a box around the table is in reach");
	}
}
