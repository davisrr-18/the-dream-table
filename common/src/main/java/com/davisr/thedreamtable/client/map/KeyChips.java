package com.davisr.thedreamtable.client.map;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * The look of map mode's controls bar: a key in a framed chip with what it does beside it, on a dark translucent panel. Shared by
 * {@link MapScreen} and the capture panel, so the two read as one family.
 */
public final class KeyChips {
	/** The panel's inner padding, a row's height and the gap between two items. */
	public static final int PADDING = 5;
	public static final int ROW = 14;
	public static final int ITEM_GAP = 12;
	public static final int PANEL_COLOR = 0x99000000;
	public static final int KEY_BORDER = 0xFF8CD8FF;
	public static final int KEY_TEXT = 0xFFFFFFFF;
	public static final int ACTION_TEXT = 0xFFD8D8D8;
	private static final int KEY_PAD = 3;
	private static final int KEY_FILL = 0xCC1E2A33;

	private KeyChips() {
	}

	/** The width of one item: the chip, a small gap and the action. */
	public static int width(TextRenderer text, Text key, Text action) {
		return text.getWidth(key) + 2 * KEY_PAD + 4 + text.getWidth(action);
	}

	public static void draw(DrawContext context, TextRenderer text, Text key, Text action, int x, int y) {
		int keyWidth = text.getWidth(key) + 2 * KEY_PAD;
		context.fill(x, y, x + keyWidth, y + 12, KEY_BORDER);
		context.fill(x + 1, y + 1, x + keyWidth - 1, y + 11, KEY_FILL);
		context.drawTextWithShadow(text, key, x + KEY_PAD, y + 2, KEY_TEXT);
		context.drawTextWithShadow(text, action, x + keyWidth + 4, y + 2, ACTION_TEXT);
	}
}
