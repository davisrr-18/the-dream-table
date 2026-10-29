package com.davisr.thedreamtable.farm.schematic;

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
 * Removes click events from the text of signs in a player's schematic. In 1.21.1 a sign keeps {@code front_text} and
 * {@code back_text}, each with {@code messages} and {@code filtered_messages}: lists of text components written as JSON
 * strings. A {@code run_command} click event on one runs for whoever clicks the sign, so a crafted file must not bring one.
 */
final class SignText {
	private static final String[] FACES = {"front_text", "back_text"};
	private static final String[] LINES = {"messages", "filtered_messages"};
	private static final String EMPTY = "\"\"";

	private SignText() {
	}

	/** Cleans the sign text inside this block entity data, in place. Data of other blocks has none of these keys and is left alone. */
	static void strip(NbtCompound data) {
		for (String face : FACES) {
			if (!data.contains(face, NbtElement.COMPOUND_TYPE)) {
				continue;
			}
			NbtCompound text = data.getCompound(face);
			for (String lines : LINES) {
				if (text.contains(lines, NbtElement.LIST_TYPE)) {
					NbtList list = (NbtList) text.get(lines);
					for (int index = 0; index < list.size(); index++) {
						// A line that is not a string is not a shape the game reads, so it can only be there to hide something.
						String line = list.get(index) instanceof NbtString string ? string.asString() : null;
						list.set(index, NbtString.of(line == null ? EMPTY : clean(line)));
					}
				}
			}
		}
	}

	/** The component with every {@code clickEvent} removed; a line that isn't valid JSON becomes an empty one. */
	static String clean(String json) {
		try {
			JsonElement component = JsonParser.parseString(json);
			removeClickEvents(component);
			return component.toString();
		} catch (JsonParseException | IllegalStateException | StackOverflowError broken) {
			return EMPTY;
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
}
