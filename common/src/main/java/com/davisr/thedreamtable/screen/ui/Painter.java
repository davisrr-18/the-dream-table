package com.davisr.thedreamtable.screen.ui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import com.davisr.thedreamtable.screen.ui.layout.UiRect;
import com.davisr.thedreamtable.screen.ui.layout.ScreenGeometry;

import static com.davisr.thedreamtable.screen.ui.Theme.*;

public final class Painter {
	private final TextRenderer text;

	public Painter(TextRenderer text) {
		this.text = text;
	}

	public TextRenderer text() {
		return text;
	}

	public void panel(DrawContext context, int x, int y, int height, Text title, Text subtitle) {
		panel(context, x, y, PANEL_WIDTH, height, title, subtitle);
	}

	public void panel(DrawContext context, int x, int y, int w, int height, Text title, Text subtitle) {
		int header = ScreenGeometry.HEADER_HEIGHT;
		context.fill(x + 3, y + 4, x + w + 3, y + height + 4, 0x30000000);

		fillTop(context, x - 1, y - 1, x + w + 1, y + header, 0xFF0A2A4E);
		fillTop(context, x, y, x + w, y + header - 1, HEADER_COLOR);
		context.fill(x + 2, y, x + w - 2, y + 1, 0xFF7FEBFD);
		context.fill(x + 1, y + 1, x + w - 1, y + 2, 0xFF3FA6E0);
		context.fill(x, y + 2, x + 1, y + header - 4, 0xFF4FB8F0);
		context.fill(x + 1, y + 2, x + 2, y + header - 4, 0xFF3A8ED0);
		context.fill(x + w - 2, y + 2, x + w - 1, y + header - 4, 0xFF2160A0);
		context.fill(x + w - 1, y + 2, x + w, y + header - 1, 0xFF1D4F85);
		context.fill(x + 2, y + header - 5, x + w - 2, y + header - 4, 0xFF3C88C8);
		context.fill(x, y + header - 4, x + w, y + header - 1, 0xFF1B4E86);
		context.fill(x, y + header - 1, x + w, y + header, 0xFF0A2A4E);

		context.fill(x, y + header, x + w, y + height, PANEL);
		context.fill(x, y + header, x + 1, y + height, 0xFFFFFAF0);
		context.fill(x + w - 2, y + header, x + w - 1, y + height, 0xFFE6DCC9);
		context.fill(x + w - 1, y + header, x + w, y + height, 0xFFC8BBA3);
		context.fill(x, y + height - 2, x + w, y + height - 1, 0xFFC9BDA8);
		context.fill(x, y + height - 1, x + w, y + height, 0xFF948877);
		context.drawBorder(x + 3, y + header + 3, w - 6, height - header - 7, 0xFFE3D8C6);

		mark(context, x + 12, y + 14);
		int textLimit = w - 46;
		fitted(context, title, x + 34, y + 10, textLimit, TITLE, false);
		fitted(context, subtitle, x + 34, y + 24, textLimit, SUBTITLE, false);
	}

	public void tab(DrawContext context, ItemStack icon, Text label, UiRect bounds, boolean hovered, boolean selected, boolean compact) {
		int fill = selected ? CARD_FILL_SELECTED : hovered ? CARD_FILL_HOVER : CARD_FILL;
		int edge = selected ? CYAN : BLUE;
		ring(context, bounds.x(), bounds.y(), bounds.width(), bounds.height(), NAVY);
		ring(context, bounds.x() + 1, bounds.y() + 1, bounds.width() - 2, bounds.height() - 2, edge);
		context.fill(bounds.x() + 2, bounds.y() + 2, bounds.right() - 2, bounds.bottom() - 2, fill);
		int iconX = compact ? bounds.x() + (bounds.width() - 16) / 2 : bounds.x() + 7;
		context.drawItem(icon, iconX, bounds.y() + 7);
		if (!compact) {
			fitted(context, label, bounds.x() + 27, bounds.y() + 11, bounds.width() - 31, NAME, false);
		}
	}

	public void card(DrawContext context, ItemStack icon, Text label, int x, int y, boolean hovered, boolean selected, boolean enabled) {
		boolean hover = enabled && hovered;
		int w = CARD_WIDTH;
		if (!enabled) {
			disabledFrame(context, x, y, w, CARD_HEIGHT);
		} else if (selected) {
			ring(context, x, y, w, CARD_HEIGHT, 0xFF1772B9);
			ring(context, x + 1, y + 1, w - 2, CARD_HEIGHT - 2, CYAN);
			ring(context, x + 2, y + 2, w - 4, CARD_HEIGHT - 4, 0xFF2A9AE0);
			context.fill(x + 3, y + 3, x + w - 3, y + CARD_HEIGHT - 3, 0xFFFFFFFF);
			context.fill(x + 4, y + 4, x + w - 4, y + CARD_HEIGHT - 4, CARD_FILL_SELECTED);
		} else {
			frame(context, x, y, w, CARD_HEIGHT, NAVY, hover ? BLUE_HOVER : BLUE, NAVY, hover ? CARD_FILL_HOVER : CARD_FILL, CARD_SHADE, 0xFFFFFFFF);
		}
		iconBox(context, x + 7, y + 6);
		context.drawItem(icon, x + 9, y + 8);
		fitted(context, label, x + 34, y + 12, CARD_TEXT_WIDTH, enabled ? NAME : MUTED, false);
	}

	/** A card with a second, smaller line under the name, such as what an option does. */
	public void card(DrawContext context, ItemStack icon, Text label, Text detail, int x, int y, boolean hovered, boolean selected, boolean enabled) {
		card(context, icon, Text.empty(), x, y, hovered, selected, enabled);
		fitted(context, label, x + 34, y + 7, CARD_TEXT_WIDTH, enabled ? NAME : MUTED, false);
		fitted(context, detail, x + 34, y + 19, CARD_TEXT_WIDTH, HINT, false);
	}

	public void button(DrawContext context, Text label, int x, int y, boolean hovered, boolean enabled, boolean primary) {
		button(context, label, x, y, CARD_WIDTH, hovered, enabled, primary);
	}

	public void button(DrawContext context, Text label, int x, int y, int w, boolean hovered, boolean enabled, boolean primary) {
		boolean hover = enabled && hovered;
		int color;
		if (!enabled) {
			disabledFrame(context, x, y, w, BUTTON_HEIGHT);
			color = MUTED;
		} else if (primary) {
			frame(context, x, y, w, BUTTON_HEIGHT, NAVY, hover ? 0xFF5BB8F0 : 0xFF3FA6E0, NAVY, hover ? 0xFF3B82BE : ACCENT, 0xFF1F5E98, 0xFF63A8E0);
			color = TITLE;
		} else {
			frame(context, x, y, w, BUTTON_HEIGHT, NAVY, hover ? BLUE_HOVER : BLUE, NAVY, hover ? CARD_FILL_HOVER : CARD_FILL, CARD_SHADE, 0xFFFFFFFF);
			color = NAME;
		}
		fitted(context, label, x + 8, y + 10, w - 16, color, true);
	}

	/** A button with a smaller second line under the label, such as what it does or why it is off. */
	public void button(DrawContext context, Text label, Text detail, int x, int y, boolean hovered, boolean enabled, boolean primary) {
		button(context, label, detail, x, y, CARD_WIDTH, hovered, enabled, primary);
	}

	public void button(DrawContext context, Text label, Text detail, int x, int y, int w, boolean hovered, boolean enabled, boolean primary) {
		button(context, Text.empty(), x, y, w, hovered, enabled, primary);
		int color = !enabled ? MUTED : primary ? TITLE : NAME;
		fitted(context, label, x + 8, y + 5, w - 16, color, true);
		fitted(context, detail, x + 8, y + 15, w - 16, primary && enabled ? TITLE : HINT, true);
	}

	public void destructiveButton(DrawContext context, Text label, int x, int y, boolean hovered, boolean enabled) {
		destructiveButton(context, label, x, y, CARD_WIDTH, hovered, enabled);
	}

	public void destructiveButton(DrawContext context, Text label, int x, int y, int w, boolean hovered, boolean enabled) {
		int color = enabled ? TITLE : MUTED;
		if (!enabled) {
			disabledFrame(context, x, y, w, BUTTON_HEIGHT);
		} else {
			frame(context, x, y, w, BUTTON_HEIGHT, 0xFF5B1714, hovered ? 0xFFD65A52 : ERROR,
					0xFF5B1714, hovered ? 0xFFC64A43 : 0xFFA8322D, 0xFF76221E, 0xFFE97B74);
		}
		fitted(context, label, x + 8, y + 10, w - 16, color, true);
	}

	/** A small button, {@code height} pixels tall, for filters and sorting. */
	public void chip(DrawContext context, Text label, int x, int y, int width, int height, boolean hovered) {
		frame(context, x, y, width, height, NAVY, hovered ? BLUE_HOVER : BLUE, NAVY, hovered ? CARD_FILL_HOVER : CARD_FILL, CARD_SHADE, 0xFFFFFFFF);
		fitted(context, label, x + 6, y + (height - 8) / 2, width - 12, NAME, true);
	}

	/**
	 * The keyboard focus ring, drawn inside the control's own bounds over its frame: navy, white, then theme cyan, so it
	 * reads on both the cream panel and a selected (cyan) card.
	 */
	public void focusRing(DrawContext context, int x, int y, int w, int h) {
		ring(context, x, y, w, h, NAVY);
		ring(context, x + 1, y + 1, w - 2, h - 2, TITLE);
		ring(context, x + 2, y + 2, w - 4, h - 4, CYAN);
	}

	public void field(DrawContext context, int x, int y, int width, int height, boolean focused) {
		ring(context, x, y, width, height, NAVY);
		ring(context, x + 1, y + 1, width - 2, height - 2, focused ? CYAN : BLUE);
		context.fill(x + 2, y + 2, x + width - 2, y + height - 2, CARD_FILL);
	}

	public void progress(DrawContext context, int x, int y, int width, int done, int total) {
		context.fill(x, y, x + width, y + 6, 0xFF163E66);
		if (total > 0) {
			int filled = Math.max(0, width * done / total);
			context.fill(x, y, x + filled, y + 6, 0xFF3E86C4);
		}
	}

	/** A horizontal slider: the filled part of the track up to {@code fraction}, and a knob there. 12 px tall. */
	public void slider(DrawContext context, int x, int y, int width, float fraction, boolean active) {
		int knob = x + Math.round((width - 6) * Math.max(0.0F, Math.min(1.0F, fraction)));
		context.fill(x, y + 4, x + width, y + 8, 0xFF163E66);
		context.fill(x, y + 4, knob + 3, y + 8, active ? CYAN : 0xFF3E86C4);
		context.fill(knob - 1, y - 1, knob + 7, y + 13, NAVY);
		context.fill(knob, y, knob + 6, y + 12, active ? 0xFFFFFFFF : CARD_FILL);
		context.fill(knob + 1, y + 1, knob + 5, y + 11, active ? CARD_FILL_SELECTED : 0xFFFFFFFF);
	}

	public void slotFrame(DrawContext context, int x, int y) {
		context.fill(x - 2, y - 2, x + 18, y + 18, 0xFF6F95BA);
		context.fill(x - 1, y - 1, x + 17, y + 17, 0xFFF3EEE4);
	}

	public void ring(DrawContext context, int x, int y, int w, int h, int color) {
		context.fill(x + 1, y, x + w - 1, y + 1, color);
		context.fill(x + 1, y + h - 1, x + w - 1, y + h, color);
		context.fill(x, y + 1, x + 1, y + h - 1, color);
		context.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
	}

	/**
	 * Draws the text in a box {@code width} wide: as is when it fits (centered in the box when asked), otherwise scrolling
	 * back and forth inside the box like vanilla button labels, so long text in any language stays readable.
	 */
	public void fitted(DrawContext context, Text label, int x, int y, int width, int color, boolean centered) {
		OrderedText line = label.asOrderedText();
		int textWidth = text.getWidth(line);
		if (textWidth <= width) {
			context.drawText(text, line, centered ? x + (width - textWidth) / 2 : x, y, color, false);
			return;
		}
		context.enableScissor(x, y - 1, x + width, y + 10);
		context.drawText(text, line, x - Marquee.offset(textWidth, width, Util.getMeasuringTimeMs()), y, color, false);
		context.disableScissor();
	}

	public boolean fits(Text label, int width) {
		return text.getWidth(label) <= width;
	}

	public String trim(Text label, int width) {
		String raw = label.getString();
		if (text.getWidth(raw) <= width) {
			return raw;
		}
		String ellipsis = "...";
		return text.trimToWidth(raw, Math.max(0, width - text.getWidth(ellipsis))) + ellipsis;
	}

	private void disabledFrame(DrawContext context, int x, int y, int w, int h) {
		frame(context, x, y, w, h, 0xFF7C8794, 0xFFA9B3BE, 0xFF7C8794, 0xFFE4DDCF, 0xFFD3CBBB, 0xFFEFE9DD);
	}

	private void frame(DrawContext context, int x, int y, int w, int h, int outer, int middle, int inner, int fill, int shade, int highlight) {
		ring(context, x, y, w, h, outer);
		ring(context, x + 1, y + 1, w - 2, h - 2, middle);
		ring(context, x + 2, y + 2, w - 4, h - 4, inner);
		context.fill(x + 3, y + 3, x + w - 3, y + h - 3, fill);
		context.fill(x + 3, y + h - 4, x + w - 3, y + h - 3, shade);
		context.fill(x + w - 4, y + 3, x + w - 3, y + h - 3, shade);
		context.fill(x + 3, y + 3, x + w - 4, y + 4, highlight);
	}

	private void iconBox(DrawContext context, int x, int y) {
		context.fill(x, y, x + 20, y + 20, 0xFF6F95BA);
		context.fill(x + 1, y + 1, x + 19, y + 19, 0xFFF3EEE4);
	}

	private void fillTop(DrawContext context, int x1, int y1, int x2, int y2, int color) {
		context.fill(x1 + 2, y1, x2 - 2, y1 + 1, color);
		context.fill(x1 + 1, y1 + 1, x2 - 1, y1 + 2, color);
		context.fill(x1, y1 + 2, x2, y2, color);
	}

	private void mark(DrawContext context, int x, int y) {
		context.fill(x + 1, y + 1, x + 19, y + 19, 0xFF1F5489);
		context.fill(x, y, x + 18, y + 18, 0xFFF4FBFF);
		context.fill(x + 2, y + 2, x + 16, y + 16, 0xFF245C98);
		context.fill(x + 6, y + 2, x + 7, y + 16, 0xFF4E92D4);
		context.fill(x + 11, y + 2, x + 12, y + 16, 0xFF4E92D4);
		context.fill(x + 2, y + 6, x + 16, y + 7, 0xFF4E92D4);
		context.fill(x + 2, y + 11, x + 16, y + 12, 0xFF4E92D4);
	}
}
