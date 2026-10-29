package com.davisr.thedreamtable.farm.schematic;

import com.davisr.thedreamtable.GameSetup;
import com.davisr.thedreamtable.farm.ImportNote;
import com.davisr.thedreamtable.farm.FarmTemplate;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtInt;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.DynamicRegistryManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What a crafted or unusual player file does to the importer: names, spans, template conflicts, tables and signs. */
class SchematicLimitsTest {
	@BeforeAll
	static void setUp() {
		GameSetup.bootstrap();
	}

	/** A structure block file with these palette names and blocks as {x, y, z, palette index}. */
	private static NbtCompound structure(List<String> palette, int[]... blocks) {
		NbtCompound root = new NbtCompound();
		NbtList names = new NbtList();
		for (String name : palette) {
			NbtCompound entry = new NbtCompound();
			entry.putString("Name", name);
			names.add(entry);
		}
		root.put("palette", names);
		NbtList list = new NbtList();
		for (int[] block : blocks) {
			NbtCompound entry = new NbtCompound();
			NbtList pos = new NbtList();
			pos.add(NbtInt.of(block[0]));
			pos.add(NbtInt.of(block[1]));
			pos.add(NbtInt.of(block[2]));
			entry.put("pos", pos);
			entry.putInt("state", block[3]);
			list.add(entry);
		}
		root.put("blocks", list);
		return root;
	}

	private static SchematicReader.Result read(NbtCompound root) {
		return SchematicReader.fromRoot("nbt:crafted", root, true);
	}

	private static ImportNote only(SchematicReader.Result result) {
		assertEquals(1, result.notes().size());
		return result.notes().get(0);
	}

	private static byte[] encoded(ImportNote note) {
		RegistryByteBuf out = new RegistryByteBuf(Unpooled.buffer(), DynamicRegistryManager.EMPTY);
		ImportNote.CODEC.encode(out, note);
		byte[] bytes = new byte[out.readableBytes()];
		out.readBytes(bytes);
		return bytes;
	}

	@Test
	void aHugePaletteNameIsCutWhereItIsCollected() {
		SchematicReader.Result result = read(structure(List.of("minecraft:stone", "x".repeat(70_000)), new int[]{0, 0, 0, 0}, new int[]{1, 0, 0, 1}));
		ImportNote note = only(result);
		assertEquals(ImportNote.Kind.UNKNOWN_BLOCKS, note.kind());
		assertEquals(ImportNote.MAX_NAME, note.names().get(0).length());
	}

	@Test
	void aNoteWithOversizedOrTooManyNamesStillEncodesAndReadsBack() {
		ImportNote note = new ImportNote(ImportNote.Kind.UNKNOWN_BLOCKS, 9, List.of("a".repeat(70_000), "b", "c", "d", "e"));
		RegistryByteBuf in = new RegistryByteBuf(Unpooled.wrappedBuffer(encoded(note)), DynamicRegistryManager.EMPTY);
		ImportNote read = ImportNote.CODEC.decode(in);
		assertEquals(0, in.readableBytes());
		assertEquals(ImportNote.Kind.UNKNOWN_BLOCKS, read.kind());
		assertEquals(ImportNote.SHOWN_NAMES, read.names().size());
		assertEquals(ImportNote.MAX_NAME, read.names().get(0).length());
	}

	@Test
	void cleaningNeverSplitsASurrogatePairOrKeepsControlCharacters() {
		String name = "a".repeat(ImportNote.MAX_NAME - 1) + "😀";
		assertEquals(ImportNote.MAX_NAME - 1, ImportNote.clean(name).length());
		assertEquals("a?b", ImportNote.clean("a\nb"));
	}

	@Test
	void aSideLongerThanTheSpanIsRejectedInEveryFormat() {
		SchematicReader.Result result = read(structure(List.of("minecraft:stone"), new int[]{0, 0, 0, 0}, new int[]{600, 0, 0, 0}));
		assertNull(result.template());
		assertEquals(ImportNote.Kind.TOO_WIDE, only(result).kind());
		assertEquals(601, only(result).count());
		assertNotNull(read(structure(List.of("minecraft:stone"), new int[]{0, 0, 0, 0}, new int[]{SchematicReader.MAX_SPAN - 1, 0, 0, 0})).template());
	}

	@Test
	void coordinatesAcrossTheWholeIntRangeCannotOverflowTheHeight() {
		SchematicReader.Result result = read(structure(List.of("minecraft:stone"), new int[]{0, Integer.MIN_VALUE, 0, 0}, new int[]{0, Integer.MAX_VALUE, 0, 0}));
		assertNull(result.template());
		assertEquals(ImportNote.Kind.TOO_TALL, only(result).kind());
		assertTrue(only(result).count() > SchematicReader.MAX_HEIGHT);
	}

	@Test
	void aSpongeFileLongerThanTheSpanIsRejectedBeforeItsCellsAreWalked() {
		NbtCompound root = new NbtCompound();
		root.putInt("Width", SchematicReader.MAX_SPAN + 1);
		root.putInt("Height", 1);
		root.putInt("Length", 1);
		root.putByteArray("BlockData", new byte[]{1});
		NbtCompound palette = new NbtCompound();
		palette.putInt("minecraft:air", 0);
		palette.putInt("minecraft:stone", 1);
		root.put("Palette", palette);
		SchematicReader.Result result = read(root);
		assertNull(result.template());
		assertEquals(ImportNote.Kind.TOO_WIDE, only(result).kind());
		assertEquals(SchematicReader.MAX_SPAN + 1, only(result).count());
	}

	@Test
	void twoBlocksInTheSameSpotAreReportedAsAnOverlapNotAsUnreadable() {
		SchematicReader.Result result = read(structure(List.of("minecraft:stone", "minecraft:dirt"), new int[]{0, 0, 0, 0}, new int[]{0, 0, 0, 1}));
		assertNull(result.template());
		assertEquals(ImportNote.Kind.OVERLAP, only(result).kind());
		assertTrue(only(result).rejects());
	}

	@Test
	void moreMaterialsThanASlotListHoldsIsReportedAsSuch() {
		String[] colors = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown",
				"green", "red", "black"};
		List<String> palette = new ArrayList<>();
		for (String color : colors) {
			palette.add("minecraft:" + color + "_wool");
			palette.add("minecraft:" + color + "_concrete");
			palette.add("minecraft:" + color + "_terracotta");
			palette.add("minecraft:" + color + "_glazed_terracotta");
		}
		assertTrue(palette.size() > FarmTemplate.MAX_SLOTS);
		int[][] blocks = new int[palette.size()][];
		for (int index = 0; index < blocks.length; index++) {
			blocks[index] = new int[]{index, 0, 0, index};
		}
		SchematicReader.Result result = read(structure(palette, blocks));
		assertNull(result.template());
		assertEquals(ImportNote.Kind.TOO_MANY_MATERIALS, only(result).kind());
	}

	@Test
	void dreamTableBlocksAreLeftOutAndCounted() {
		SchematicReader.Result result = read(structure(List.of("minecraft:stone", "the_dream_table:dream_table"),
				new int[]{0, 0, 0, 0}, new int[]{1, 0, 0, 1}, new int[]{2, 0, 0, 1}));
		assertNotNull(result.template());
		assertEquals(1, result.template().steps().size());
		assertEquals(ImportNote.Kind.TABLE_BLOCKS, only(result).kind());
		assertEquals(2, only(result).count());
	}

	@Test
	void aDreamTableAloneLeavesAnEmptyFile() {
		SchematicReader.Result result = read(structure(List.of("the_dream_table:dream_table"), new int[]{0, 0, 0, 0}));
		assertNull(result.template());
		assertEquals(ImportNote.Kind.EMPTY, only(result).kind());
	}

	private static NbtCompound signStructure(String... lines) {
		NbtCompound root = structure(List.of("minecraft:oak_sign"), new int[]{0, 0, 0, 0});
		NbtCompound data = new NbtCompound();
		NbtCompound front = new NbtCompound();
		NbtList messages = new NbtList();
		for (String line : lines) {
			messages.add(NbtString.of(line));
		}
		front.put("messages", messages);
		front.put("filtered_messages", messages.copy());
		data.put("front_text", front);
		data.put("back_text", front.copy());
		data.putBoolean("is_waxed", false);
		root.getList("blocks", 10).getCompound(0).put("nbt", data);
		return root;
	}

	@Test
	void signClickEventsAreRemovedButTheTextStays() {
		String command = "{\"text\":\"Click\",\"clickEvent\":{\"action\":\"run_command\",\"value\":\"/op me\"},\"extra\":[{\"text\":\"more\",\"clickEvent\":{\"action\":\"run_command\",\"value\":\"/stop\"}}]}";
		NbtCompound kept = read(signStructure(command, "\"plain\"")).template().steps().get(0).blockNbt();
		assertNotNull(kept);
		for (String face : List.of("front_text", "back_text")) {
			for (String key : List.of("messages", "filtered_messages")) {
				NbtList lines = kept.getCompound(face).getList(key, 8);
				assertEquals(2, lines.size());
				assertFalse(lines.getString(0).contains("clickEvent"), lines.getString(0));
				assertFalse(lines.getString(0).contains("/op"));
				assertTrue(lines.getString(0).contains("Click") && lines.getString(0).contains("more"));
				assertEquals("\"plain\"", lines.getString(1));
			}
		}
		assertFalse(kept.getBoolean("is_waxed"));
	}

	@Test
	void aSignLineThatIsNotValidTextBecomesEmpty() {
		NbtCompound kept = read(signStructure("{\"text\":", "\"ok\"")).template().steps().get(0).blockNbt();
		NbtList lines = kept.getCompound("front_text").getList("messages", 8);
		assertEquals("\"\"", lines.getString(0));
		assertEquals("\"ok\"", lines.getString(1));
	}

	@Test
	void bundledFarmsKeepTheirSignsAsDesigned() {
		String command = "{\"text\":\"Click\",\"clickEvent\":{\"action\":\"run_command\",\"value\":\"/say hi\"}}";
		NbtCompound kept = SchematicReader.fromRoot("nbt:bundled", signStructure(command), false).template().steps().get(0).blockNbt();
		assertTrue(kept.getCompound("front_text").getList("messages", 8).getString(0).contains("clickEvent"));
	}
}
