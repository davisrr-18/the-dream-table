package com.davisr.thedreamtable.screen.ui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** Draws the mod's own tooltips wrapped to a width that always fits the window; vanilla places them inside the screen. */
public final class Tooltips {
	public static final int MAX_WIDTH = 200;
	public static final int MIN_WIDTH = 80;
	/** Vanilla offsets a tooltip 12 px from the mouse and flips it to the left of the mouse when it would leave the window. */
	/** Vanilla puts a tooltip flipped left of the mouse 24 px away; plus its frame, so it never covers the pointer. */
	private static final int MOUSE_GAP = 32;

	private Tooltips() {
	}

	/**
	 * The widest a tooltip's text may be in a window {@code screenWidth} scaled pixels wide: at most {@link #MAX_WIDTH},
	 * and under half the window so that, flipped to the left of the mouse, it still fits.
	 */
	public static int maxWidth(int screenWidth) {
		return Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, screenWidth / 2 - MOUSE_GAP));
	}

	/** Each line wrapped to {@code width}; an empty line stays one empty line. */
	public static List<OrderedText> wrap(TextRenderer font, List<Text> lines, int width) {
		List<OrderedText> wrapped = new ArrayList<>();
		for (Text line : lines) {
			List<OrderedText> parts = font.wrapLines(line, width);
			if (parts.isEmpty()) {
				wrapped.add(OrderedText.EMPTY);
			} else {
				wrapped.addAll(parts);
			}
		}
		return wrapped;
	}

	public static void draw(DrawContext context, TextRenderer font, List<Text> lines, int mouseX, int mouseY) {
		context.drawOrderedTooltip(font, wrap(font, lines, maxWidth(context.getScaledWindowWidth())), mouseX, mouseY);
	}

	public static void draw(DrawContext context, TextRenderer font, Text line, int mouseX, int mouseY) {
		draw(context, font, List.of(line), mouseX, mouseY);
	}
}
