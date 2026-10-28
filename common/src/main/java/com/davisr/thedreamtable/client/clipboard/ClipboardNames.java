package com.davisr.thedreamtable.client.clipboard;

import net.minecraft.client.font.TextRenderer;

import java.util.ArrayList;
import java.util.List;

import static com.davisr.thedreamtable.client.clipboard.ClipboardLayout.*;

/** Fits a material name into its row: one line, then two lines, then a smaller font, then an ellipsis. */
public final class ClipboardNames {
	private static final float MIN_SCALE = 0.0075F;
	private static final float LINE_GAP = 0.006F;

	private ClipboardNames() {
	}

	public record Placed(List<String> lines, float scale, float height) {
		public float lineHeight(TextRenderer text) {
			return text.fontHeight * scale;
		}

		public float gap() {
			return lines.size() > 1 ? LINE_GAP : 0.0F;
		}
	}

	public static Placed place(TextRenderer text, String name, String count, float left, float right) {
		float nameX = left + ICON + 0.06F;
		float firstWorld = Math.max(0.25F, right - 0.04F - text.getWidth(count) * TEXT - nameX);
		float fullWorld = Math.max(firstWorld, right - 0.02F - nameX);
		float room = ROW_STEP - 0.05F;
		if (text.getWidth(name) * TEXT <= firstWorld) {
			return new Placed(List.of(name), TEXT, text.fontHeight * TEXT);
		}
		for (float scale = TEXT; scale >= MIN_SCALE; scale -= 0.0005F) {
			int first = Math.max(4, (int) (firstWorld / scale));
			int next = Math.max(first, (int) (fullWorld / scale));
			List<String> lines = wrap(text, name, first, next);
			if (lines == null) {
				continue;
			}
			float gap = lines.size() > 1 ? LINE_GAP : 0.0F;
			float height = lines.size() * text.fontHeight * scale + (lines.size() - 1) * gap;
			if (height <= room) {
				return new Placed(lines, scale, height);
			}
		}
		int next = Math.max(8, (int) (fullWorld / MIN_SCALE));
		return new Placed(List.of(fit(text, name, next)), MIN_SCALE, text.fontHeight * MIN_SCALE);
	}

	public static String fit(TextRenderer text, String value, int maxPixels) {
		if (text.getWidth(value) <= maxPixels) {
			return value;
		}
		String ellipsis = "...";
		return text.trimToWidth(value, Math.max(0, maxPixels - text.getWidth(ellipsis))) + ellipsis;
	}

	private static List<String> wrap(TextRenderer text, String name, int first, int next) {
		List<String> lines = new ArrayList<>();
		String rest = name;
		for (int line = 0; line < 2 && !rest.isEmpty(); line++) {
			int limit = line == 0 ? first : next;
			if (text.getWidth(rest) <= limit) {
				lines.add(rest);
				return lines;
			}
			String piece = text.trimToWidth(rest, limit);
			if (piece.isEmpty()) {
				return null;
			}
			int space = piece.lastIndexOf(' ');
			if (space >= 4) {
				piece = piece.substring(0, space);
			}
			lines.add(piece);
			rest = rest.substring(piece.length()).stripLeading();
		}
		return rest.isEmpty() ? lines : null;
	}
}
