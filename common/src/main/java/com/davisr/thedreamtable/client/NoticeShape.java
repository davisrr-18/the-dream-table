package com.davisr.thedreamtable.client;

import java.util.ArrayList;
import java.util.List;

/**
 * The look of a table notice as plain geometry: a panel with pixel-rounded corners (two pixels cut, like vanilla-style
 * rounded panels) drawn as non-overlapping rectangles, so a translucent fill and a translucent outline never stack on
 * the same pixel. Kept free of Minecraft types so it can be tested.
 */
public final class NoticeShape {
	/** Background opacity, so the world stays visible behind the text. */
	public static final float FILL_OPACITY = 0.58F;
	/** The outline is a little firmer than the fill but still soft. */
	public static final float EDGE_OPACITY = 0.7F;
	/** Smallest panel the shape is defined for. */
	public static final int MIN_SIZE = 6;

	/** A rectangle {@code [x1, x2) x [y1, y2)} in screen pixels. */
	public record Rect(int x1, int y1, int x2, int y2) {
		public int area() {
			return (x2 - x1) * (y2 - y1);
		}
	}

	private NoticeShape() {
	}

	/** The interior: everything inside the outline. */
	public static List<Rect> fill(int x, int y, int w, int h) {
		check(w, h);
		List<Rect> rects = new ArrayList<>();
		rects.add(new Rect(x + 2, y + 1, x + w - 2, y + 2));
		rects.add(new Rect(x + 1, y + 2, x + w - 1, y + h - 2));
		rects.add(new Rect(x + 2, y + h - 2, x + w - 2, y + h - 1));
		return rects;
	}

	/** The one-pixel outline, with each corner cut diagonally. */
	public static List<Rect> outline(int x, int y, int w, int h) {
		check(w, h);
		List<Rect> rects = new ArrayList<>();
		rects.add(new Rect(x + 2, y, x + w - 2, y + 1));
		rects.add(new Rect(x + 2, y + h - 1, x + w - 2, y + h));
		rects.add(new Rect(x, y + 2, x + 1, y + h - 2));
		rects.add(new Rect(x + w - 1, y + 2, x + w, y + h - 2));
		rects.add(new Rect(x + 1, y + 1, x + 2, y + 2));
		rects.add(new Rect(x + w - 2, y + 1, x + w - 1, y + 2));
		rects.add(new Rect(x + 1, y + h - 2, x + 2, y + h - 1));
		rects.add(new Rect(x + w - 2, y + h - 2, x + w - 1, y + h - 1));
		return rects;
	}

	/** {@code rgb} with an alpha of {@code opacity} (0-255) times {@code factor}. */
	public static int withAlpha(int rgb, int opacity, float factor) {
		int alpha = Math.max(0, Math.min(255, Math.round(opacity * factor)));
		return (alpha << 24) | (rgb & 0xFFFFFF);
	}

	private static void check(int w, int h) {
		if (w < MIN_SIZE || h < MIN_SIZE) {
			throw new IllegalArgumentException("notice too small: " + w + "x" + h);
		}
	}
}
