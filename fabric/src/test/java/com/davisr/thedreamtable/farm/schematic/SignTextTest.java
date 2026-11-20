package com.davisr.thedreamtable.farm.schematic;

import com.davisr.thedreamtable.DataFixes;
import com.davisr.thedreamtable.GameSetup;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtInt;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtString;
import net.minecraft.text.TextCodecs;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sign lines of a player's file. 1.21.1 wrote each line as a JSON string; the game from data version 4290 on reads a line
 * as a string of plain text or as a text component compound. Click events never survive either form.
 */
public class SignTextTest {
	/** The data version of 1.21.1, which wrote lines as JSON strings. */
	static final int FLAT = DataFixes.ASSUMED;

	@BeforeAll
	static void setUp() {
		GameSetup.bootstrap();
	}

	/** The text the game shows for a sign line, read the way the game reads it. */
	public static String shown(NbtElement line) {
		return TextCodecs.CODEC.parse(NbtOps.INSTANCE, line).getOrThrow().getString();
	}

	/** The line after stripping it as the front message of a sign written at this data version. */
	private static NbtElement stripped(NbtElement line, int from) {
		NbtList messages = new NbtList();
		messages.add(line);
		NbtCompound front = new NbtCompound();
		front.put("messages", messages);
		NbtCompound data = new NbtCompound();
		data.put("front_text", front);
		SignText.strip(data, from);
		return data.getCompoundOrEmpty("front_text").getListOrEmpty("messages").get(0);
	}

	private static void assertNoClickEvent(NbtElement line) {
		String text = line.toString().toLowerCase();
		assertFalse(text.contains("click"), text);
		assertFalse(text.contains("/op"), text);
	}

	@Test
	void aFlatLineFromOlderDataReadsAsItsText() {
		NbtElement line = stripped(NbtString.of("{\"text\":\"Hello\"}"), FLAT);
		assertEquals("Hello", shown(line));
	}

	@Test
	void aFlatLineLosesItsClickEventsAndKeepsItsText() {
		String json = "{\"text\":\"Oi\",\"clickEvent\":{\"action\":\"run_command\",\"value\":\"/op me\"},"
				+ "\"extra\":[{\"text\":\"!\",\"clickEvent\":{\"action\":\"run_command\",\"value\":\"/stop\"}}]}";
		NbtElement line = stripped(NbtString.of(json), FLAT);
		assertEquals("Oi!", shown(line));
		assertNoClickEvent(line);
	}

	@Test
	void aLineInTheCurrentFormatStaysPlainTextWithoutQuotes() {
		NbtElement line = stripped(NbtString.of("Hello"), DataFixes.current());
		assertEquals(NbtString.of("Hello"), line);
		assertEquals("Hello", shown(line));
	}

	@Test
	void aCurrentCompoundLineKeepsItsTextAndLosesItsClickEvent() {
		NbtCompound component = new NbtCompound();
		component.putString("text", "Oi");
		NbtCompound click = new NbtCompound();
		click.putString("action", "run_command");
		click.putString("command", "/op me");
		component.put("click_event", click);
		NbtElement line = stripped(component, DataFixes.current());
		assertEquals("Oi", shown(line));
		assertNoClickEvent(line);
	}

	@Test
	void aLineThatIsNeitherStringNorCompoundBecomesEmpty() {
		assertEquals(NbtString.of(""), stripped(NbtInt.of(5), DataFixes.current()));
		assertEquals(NbtString.of(""), stripped(NbtInt.of(5), FLAT));
	}

	@Test
	void anEmptyFlatLineStaysEmptyInsteadOfShowingNull() {
		assertEquals(NbtString.of(""), stripped(NbtString.of(""), FLAT));
	}

	@Test
	void theGameReadsFlatJsonAsTextUntilTheSchemaThatWritesNbt() {
		NbtElement json = NbtString.of("{\"text\":\"Hi\"}");
		assertTrue(DataFixes.textComponent(json, 4289) instanceof NbtCompound);
		assertEquals(json, DataFixes.textComponent(json, 4290));
	}
}
