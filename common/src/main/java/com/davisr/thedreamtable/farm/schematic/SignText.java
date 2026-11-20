package com.davisr.thedreamtable.farm.schematic;

import com.davisr.thedreamtable.DataFixes;
import com.davisr.thedreamtable.Nbts;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;

import java.util.Map;

/**
 * Reads the text of signs in a schematic and removes its click events. A sign keeps {@code front_text} and
 * {@code back_text}, each with {@code messages} and {@code filtered_messages}. Up to data version 4289 a line is a text
 * component written as a JSON string; from 4290 it is a plain string or a text component compound. A {@code run_command}
 * click event on a sign runs for whoever clicks it, so a crafted file must not bring one.
 */
public final class SignText {
	/** The first data version whose sign lines are NBT; older lines are JSON strings to convert with {@link DataFixes#textComponent}. */
	static final int NBT_LINES = 4290;
	private static final String[] FACES = {"front_text", "back_text"};
	private static final String[] LINES = {"messages", "filtered_messages"};

	private SignText() {
	}

	/**
	 * Cleans the sign text inside this block entity data, in place, for a file written at data version {@code from}. Data of
	 * other blocks has none of these keys and is left alone.
	 */
	public static void strip(NbtCompound data, int from) {
		for (String face : FACES) {
			if (!Nbts.hasCompound(data, face)) {
				continue;
			}
			NbtCompound text = data.getCompoundOrEmpty(face);
			for (String lines : LINES) {
				if (Nbts.hasList(text, lines)) {
					NbtList converted = new NbtList();
					for (NbtElement line : (NbtList) text.get(lines)) {
						converted.add(line(line, from));
					}
					text.put(lines, converted);
				}
			}
		}
	}

	/** A line in the current format. A line that is neither a string nor a compound is not a shape the game reads, so it becomes empty. */
	private static NbtElement line(NbtElement line, int from) {
		if (from < NBT_LINES) {
			String json = line instanceof NbtString string ? string.value() : null;
			String cleaned = json == null ? null : clean(json);
			return cleaned == null ? NbtString.of("") : withoutClickEvents(DataFixes.textComponent(NbtString.of(cleaned), from));
		}
		if (line instanceof NbtString) {
			return line;
		}
		return line instanceof NbtCompound component ? withoutClickEvents(component) : NbtString.of("");
	}

	/** The JSON with every click event removed, or null when it isn't a JSON text component. */
	private static String clean(String json) {
		try {
			JsonElement component = JsonParser.parseString(json);
			// An empty line parses as JSON null; the game shows it as no text.
			if (component.isJsonNull()) {
				return null;
			}
			removeClickEvents(component);
			return component.toString();
		} catch (JsonParseException | IllegalStateException | StackOverflowError broken) {
			return null;
		}
	}

	private static void removeClickEvents(JsonElement element) {
		if (element instanceof JsonObject object) {
			object.remove("clickEvent");
			// The name newer versions use, in case a file was written by one.
			object.remove("click_event");
			for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
				removeClickEvents(entry.getValue());
			}
		} else if (element instanceof JsonArray array) {
			for (JsonElement child : array) {
				removeClickEvents(child);
			}
		}
	}

	/** The same element with its click events removed at any depth, the name of each version's key included. */
	private static NbtElement withoutClickEvents(NbtElement element) {
		if (element instanceof NbtCompound compound) {
			compound.remove("clickEvent");
			compound.remove("click_event");
			for (String key : compound.getKeys()) {
				withoutClickEvents(compound.get(key));
			}
		} else if (element instanceof NbtList list) {
			for (NbtElement child : list) {
				withoutClickEvents(child);
			}
		}
		return element;
	}
}
