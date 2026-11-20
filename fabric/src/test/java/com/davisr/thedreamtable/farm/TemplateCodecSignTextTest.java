package com.davisr.thedreamtable.farm;

import com.davisr.thedreamtable.GameSetup;
import com.davisr.thedreamtable.farm.schematic.SignTextTest;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Sign text in a stored design: a design from before the text component schema has JSON lines, and must read as text. */
class TemplateCodecSignTextTest {
	@BeforeAll
	static void setUp() {
		GameSetup.bootstrap();
	}

	private static NbtCompound sign(NbtElement line) {
		NbtList messages = new NbtList();
		messages.add(line);
		NbtCompound front = new NbtCompound();
		front.put("messages", messages);
		NbtCompound data = new NbtCompound();
		data.put("front_text", front);
		return data;
	}

	private static NbtElement decodedLine(NbtCompound design) {
		FarmTemplate template = TemplateCodec.decode("design", design);
		return template.steps().get(0).blockNbt().getCompoundOrEmpty("front_text").getListOrEmpty("messages").get(0);
	}

	/** Designs saved before the mod stamped a data version have none, and their sign lines are JSON strings. */
	@Test
	void aDesignWithoutAVersionShowsItsSignTextAndLosesItsClickEvent() {
		String json = "{\"text\":\"Hello\",\"clickEvent\":{\"action\":\"run_command\",\"value\":\"/op me\"}}";
		FarmTemplate farm = FarmTemplate.of("design", List.of(FarmStep.block(0, 0, 0, Blocks.OAK_SIGN.getDefaultState(), null, sign(NbtString.of(json)))));
		NbtCompound design = TemplateCodec.encode(farm, true);
		design.remove("DataVersion");
		NbtElement line = decodedLine(design);
		assertEquals("Hello", SignTextTest.shown(line));
		assertFalse(line.toString().contains("/op"));
	}

	/** A design stamped with the current version is already in the current format: its lines are read as written. */
	@Test
	void aDesignStampedWithTheCurrentVersionKeepsItsLinesAsWritten() {
		String written = "{\"text\":\"Hello\"}";
		FarmTemplate farm = FarmTemplate.of("design", List.of(FarmStep.block(0, 0, 0, Blocks.OAK_SIGN.getDefaultState(), null, sign(NbtString.of(written)))));
		assertEquals(NbtString.of(written), decodedLine(TemplateCodec.encode(farm, true)));
	}
}
