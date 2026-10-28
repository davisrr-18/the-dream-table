package com.davisr.thedreamtable.client.clipboard;

import net.minecraft.client.font.TextRenderer;

/** Board geometry in block units, with the origin at the board center. */
public final class ClipboardLayout {
	public static final int ROWS_PER_PAGE = 6;
	public static final float COL_SPAN = 1.56F;
	public static final float COL_GAP = 0.18F;
	public static final float TOTALS_LEFT = -(COL_SPAN + COL_GAP * 0.5F);
	public static final float TOTALS_RIGHT = TOTALS_LEFT + COL_SPAN;
	public static final float ORDER_LEFT = COL_GAP * 0.5F;
	public static final float ORDER_RIGHT = ORDER_LEFT + COL_SPAN;
	public static final float LEFT = TOTALS_LEFT;
	public static final float RIGHT = ORDER_RIGHT;
	public static final float HALF_W = ORDER_RIGHT + 0.17F;
	public static final float HALF_H = 1.28F;
	public static final float TEXT = 0.0125F;
	public static final float TITLE_TEXT = 0.017F;
	public static final float PAGE_TEXT = 0.015F;
	public static final float ROW_TOP = 0.70F;
	public static final float ROW_STEP = 0.195F;
	public static final float ICON = 0.16F;
	public static final float TITLE_Y = 1.02F;
	public static final float COLUMN_Y = 0.82F;
	public static final float BAR_H = 0.07F;
	public static final float BUTTON_H = 0.18F;
	public static final float BUTTON_W = 0.38F;

	private ClipboardLayout() {
	}

	public enum Zone {
		NONE,
		TOTALS_PREV,
		TOTALS_NEXT,
		ORDER_PREV,
		ORDER_NEXT
	}

	/** Heights below the material rows, which depend on the font size. */
	public record Rows(float itemLabelY, float itemBarTop, float buildLabelY, float buildBarTop, float buttonY0, float buttonY1) {
		public static Rows of(TextRenderer text) {
			float body = text.fontHeight * TEXT;
			float lastRow = ROW_TOP - (ROWS_PER_PAGE - 1) * ROW_STEP;
			float itemLabelY = lastRow - body - 0.12F;
			float itemBarTop = itemLabelY - body - 0.04F;
			float buildLabelY = itemBarTop - BAR_H - 0.05F;
			float buildBarTop = buildLabelY - body - 0.04F;
			float buttonY1 = buildBarTop - BAR_H - 0.055F;
			return new Rows(itemLabelY, itemBarTop, buildLabelY, buildBarTop, buttonY1 - BUTTON_H, buttonY1);
		}
	}

	public static Zone zoneAt(Rows rows, double x, double y, int totalsPages, int orderPages) {
		if (y > rows.buttonY1() + 0.03 || y < rows.buttonY0() - 0.03) {
			return Zone.NONE;
		}
		if (totalsPages > 1 && x >= TOTALS_LEFT - 0.03 && x <= TOTALS_LEFT + BUTTON_W + 0.03) {
			return Zone.TOTALS_PREV;
		}
		if (totalsPages > 1 && x >= TOTALS_RIGHT - BUTTON_W - 0.03 && x <= TOTALS_RIGHT + 0.03) {
			return Zone.TOTALS_NEXT;
		}
		if (orderPages > 1 && x >= ORDER_LEFT - 0.03 && x <= ORDER_LEFT + BUTTON_W + 0.03) {
			return Zone.ORDER_PREV;
		}
		if (orderPages > 1 && x >= ORDER_RIGHT - BUTTON_W - 0.03 && x <= ORDER_RIGHT + 0.03) {
			return Zone.ORDER_NEXT;
		}
		return Zone.NONE;
	}

	public static int rowAt(TextRenderer text, double x, double y, int rows, float left, float right) {
		if (rows <= 0 || x < left - 0.02 || x > right + 0.02) {
			return -1;
		}
		float band = Math.max(text.fontHeight * TEXT, ICON) + 0.04F;
		for (int index = 0; index < rows; index++) {
			float top = ROW_TOP - index * ROW_STEP + 0.03F;
			if (y <= top && y >= top - band) {
				return index;
			}
		}
		return -1;
	}

	public static int pages(int lines) {
		return Math.max(1, (lines + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
	}
}
