package com.davisr.thedreamtable.farm.schematic;

import com.davisr.thedreamtable.GameSetup;
import com.davisr.thedreamtable.farm.ImportNote;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A crafted schematic must be refused before it is allocated or walked, never after. */
class SchematicBoundsTest {
	private static final Duration QUICK = Duration.ofSeconds(5);

	@TempDir
	Path folder;

	@BeforeAll
	static void setUp() {
		GameSetup.bootstrap();
	}

	private static NbtCompound region(int[] position, int[] size) {
		NbtCompound region = new NbtCompound();
		region.putIntArray("Position", position);
		region.putIntArray("Size", size);
		region.put("BlockStatePalette", LitematicaTestFiles.palette("minecraft:air", "minecraft:stone"));
		region.putLongArray("BlockStates", new long[]{0x5555555555555555L});
		return region;
	}

	private static SchematicReader.Result read(NbtCompound regions) {
		NbtCompound root = new NbtCompound();
		root.put("Regions", regions);
		return assertTimeoutPreemptively(QUICK, () -> SchematicReader.fromRoot("nbt:crafted", root, true));
	}

	private static void assertTooMany(SchematicReader.Result result) {
		assertNull(result.template());
		assertEquals(1, result.notes().size());
		assertEquals(ImportNote.Kind.TOO_MANY_BLOCKS, result.notes().get(0).kind());
		assertTrue(result.notes().get(0).count() > SchematicReader.MAX_BLOCKS);
	}

	@Test
	void litematicaDimensionsFarAboveTheLimitAreRefusedWithoutWalkingThem() {
		NbtCompound regions = new NbtCompound();
		regions.put("Huge", region(new int[]{0, 0, 0}, new int[]{100000, 100000, 100000}));
		assertTooMany(read(regions));
	}

	@Test
	void litematicaSmallestIntSizeCannotWrapToANegativeCount() {
		NbtCompound regions = new NbtCompound();
		regions.put("Wrapped", region(new int[]{0, 0, 0}, new int[]{Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE}));
		assertTooMany(read(regions));
	}

	@Test
	void litematicaRegionsAreCountedTogether() {
		NbtCompound regions = new NbtCompound();
		int side = (int) Math.cbrt(SchematicReader.MAX_CELLS) - 1;
		regions.put("A", region(new int[]{0, 0, 0}, new int[]{side, side, side}));
		regions.put("B", region(new int[]{0, 0, 0}, new int[]{side, side, side}));
		assertTooMany(read(regions));
	}

	@Test
	void litematicaWithTooManyRegionsIsRefused() {
		NbtCompound regions = new NbtCompound();
		for (int index = 0; index < 1025; index++) {
			regions.put("r" + index, region(new int[]{index, 0, 0}, new int[]{1, 1, 1}));
		}
		assertTooMany(read(regions));
	}

	@Test
	void litematicaRegionsThatReachFarApartAreRefused() {
		NbtCompound regions = new NbtCompound();
		regions.put("Near", region(new int[]{0, 0, 0}, new int[]{1, 1, 1}));
		regions.put("Far", region(new int[]{Integer.MAX_VALUE - 10, 0, 0}, new int[]{1, 1, 1}));
		assertTooMany(read(regions));
	}

	@Test
	void litematicaPaletteLargerThanTheLimitIsRefused() {
		NbtCompound region = region(new int[]{0, 0, 0}, new int[]{1, 1, 1});
		String[] names = new String[SchematicReader.MAX_PALETTE + 1];
		Arrays.fill(names, "minecraft:stone");
		region.put("BlockStatePalette", LitematicaTestFiles.palette(names));
		NbtCompound regions = new NbtCompound();
		regions.put("Wide", region);
		assertTooMany(read(regions));
	}

	@Test
	void litematicaOnlyWalksTheCellsItsDataCovers() {
		NbtCompound regions = new NbtCompound();
		// 4 million cells fit the limit, but one long holds 32 of them: the rest is never looked at.
		regions.put("Sparse", region(new int[]{0, 0, 0}, new int[]{200, 200, 100}));
		SchematicReader.Result result = read(regions);
		assertEquals(32, result.template().steps().size());
	}

	@Test
	void spongeDimensionsFarAboveTheLimitAreRefused() {
		NbtCompound root = new NbtCompound();
		root.putInt("Width", Integer.MAX_VALUE);
		root.putInt("Height", Integer.MAX_VALUE);
		root.putInt("Length", Integer.MAX_VALUE);
		root.putByteArray("BlockData", new byte[]{1, 1, 1});
		NbtCompound palette = new NbtCompound();
		palette.putInt("minecraft:air", 0);
		palette.putInt("minecraft:stone", 1);
		root.put("Palette", palette);
		assertTooMany(assertTimeoutPreemptively(QUICK, () -> SchematicReader.fromRoot("nbt:crafted", root, true)));
	}

	@Test
	void spongePaletteIdFarAboveTheLimitIsRefusedWithoutAllocatingForIt() {
		NbtCompound root = new NbtCompound();
		root.putInt("Width", 1);
		root.putInt("Height", 1);
		root.putInt("Length", 1);
		root.putByteArray("BlockData", new byte[]{0});
		NbtCompound palette = new NbtCompound();
		palette.putInt("minecraft:stone", Integer.MAX_VALUE - 1);
		root.put("Palette", palette);
		assertTooMany(assertTimeoutPreemptively(QUICK, () -> SchematicReader.fromRoot("nbt:crafted", root, true)));
	}

	@Test
	void aDenseFileReportsItsRealCountButKeepsOnlyTheLimitInMemory() {
		StepCollector collector = new StepCollector(true);
		int count = SchematicReader.MAX_BLOCKS + 5000;
		for (int index = 0; index < count; index++) {
			collector.block(index, 0, 0, Blocks.STONE.getDefaultState(), null);
		}
		assertEquals(SchematicReader.MAX_BLOCKS, collector.steps().size());
		assertEquals(count, collector.total());
	}

	@Test
	void aDeclaredArrayLengthFarAboveTheDataIsRefusedBeforeItIsAllocated() throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (DataOutputStream out = new DataOutputStream(bytes)) {
			out.writeByte(10);
			out.writeUTF("");
			out.writeByte(12);
			out.writeUTF("BlockStates");
			out.writeInt(Integer.MAX_VALUE);
		}
		Path file = folder.resolve("lying.litematic");
		Files.write(file, bytes.toByteArray());
		SchematicReader.Result result = assertTimeoutPreemptively(QUICK, () -> SchematicReader.read("nbt:lying", file));
		assertNull(result.template());
		assertTrue(result.notes().get(0).rejects());
	}

	@Test
	void aDeclaredListLengthFarAboveTheDataIsRefusedBeforeItIsAllocated() throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (DataOutputStream out = new DataOutputStream(bytes)) {
			out.writeByte(10);
			out.writeUTF("");
			out.writeByte(9);
			out.writeUTF("blocks");
			out.writeByte(10);
			out.writeInt(Integer.MAX_VALUE);
		}
		Path file = folder.resolve("lying.nbt");
		Files.write(file, bytes.toByteArray());
		SchematicReader.Result result = assertTimeoutPreemptively(QUICK, () -> SchematicReader.read("nbt:lying", file));
		assertNull(result.template());
		assertTrue(result.notes().get(0).rejects());
	}

	@Test
	void aCompressedFileOfTheSameKindIsRefusedToo() throws IOException {
		NbtCompound root = new NbtCompound();
		NbtCompound regions = new NbtCompound();
		regions.put("Huge", region(new int[]{0, 0, 0}, new int[]{100000, 100000, 100000}));
		root.put("Regions", regions);
		Path file = folder.resolve("huge.litematic");
		NbtIo.writeCompressed(root, file);
		assertTooMany(assertTimeoutPreemptively(QUICK, () -> SchematicReader.read("nbt:huge", file)));
	}
}
