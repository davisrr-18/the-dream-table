package com.davisr.thedreamtable.screen.ui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

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
		int w = PANEL_WIDTH;
		context.fill(x + 3, y + 4, x + w + 3, y + height + 4, 0x30000000);

		fillTop(context, x - 1, y - 1, x + w + 1, y + HEADER, 0xFF0A2A4E);
		fillTop(context, x, y, x + w, y + HEADER - 1, HEADER_COLOR);
		context.fill(x + 2, y, x + w - 2, y + 1, 0xFF7FEBFD);
		context.fill(x + 1, y + 1, x + w - 1, y + 2, 0xFF3FA6E0);
		context.fill(x, y + 2, x + 1, y + HEADER - 4, 0xFF4FB8F0);
		context.fill(x + 1, y + 2, x + 2, y + HEADER - 4, 0xFF3A8ED0);
		context.fill(x + w - 2, y + 2, x + w - 1, y + HEADER - 4, 0xFF2160A0);
		context.fill(x + w - 1, y + 2, x + w, y + HEADER - 1, 0xFF1D4F85);
		context.fill(x + 2, y + HEADER - 5, x + w - 2, y + HEADER - 4, 0xFF3C88C8);
		context.fill(x, y + HEADER - 4, x + w, y + HEADER - 1, 0xFF1B4E86);
		context.fill(x, y + HEADER - 1, x + w, y + HEADER, 0xFF0A2A4E);

		context.fill(x, y + HEADER, x + w, y + height, PANEL);
		context.fill(x, y + HEADER, x + 1, y + height, 0xFFFFFAF0);
		context.fill(x + w - 2, y + HEADER, x + w - 1, y + height, 0xFFE6DCC9);
		context.fill(x + w - 1, y + HEADER, x + w, y + height, 0xFFC8BBA3);
		context.fill(x, y + height - 2, x + w, y + height - 1, 0xFFC9BDA8);
		context.fill(x, y + height - 1, x + w, y + height, 0xFF948877);
		context.drawBorder(x + 3, y + HEADER + 3, w - 6, height - HEADER - 7, 0xFFE3D8C6);

		mark(context, x + 12, y + 14);
		int textLimit = w - 46;
		context.drawText(text, trim(title, textLimit), x + 34, y + 10, TITLE, false);
		context.drawText(text, trim(subtitle, textLimit), x + 34, y + 24, SUBTITLE, false);
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
		context.drawText(text, trim(label, w - 46), x + 34, y + 12, enabled ? NAME : MUTED, false);
	}

	/** A card with a second, smaller line under the name, such as what an option does. */
	public void card(DrawContext context, ItemStack icon, Text label, Text detail, int x, int y, boolean hovered, boolean selected, boolean enabled) {
		card(context, icon, Text.empty(), x, y, hovered, selected, enabled);
		context.drawText(text, trim(label, CARD_WIDTH - 46), x + 34, y + 7, enabled ? NAME : MUTED, false);
		context.drawText(text, trim(detail, CARD_WIDTH - 46), x + 34, y + 19, HINT, false);
	}

	/** A two-line card with a short colored tag at the right end of the name line, such as a farm's size. */
	public void card(DrawContext context, ItemStack icon, Text label, Text detail, Text tag, int tagColor, int x, int y, boolean hovered, boolean selected, boolean enabled) {
		card(context, icon, Text.empty(), x, y, hovered, selected, enabled);
		int right = x + CARD_WIDTH - 10;
		int tagWidth = text.getWidth(tag);
		context.drawText(text, tag, right - tagWidth, y + 7, enabled ? tagColor : MUTED, false);
		context.drawText(text, trim(label, right - tagWidth - 6 - (x + 34)), x + 34, y + 7, enabled ? NAME : MUTED, false);
		context.drawText(text, trim(detail, CARD_WIDTH - 46), x + 34, y + 19, HINT, false);
	}

	public void button(DrawContext context, Text label, int x, int y, boolean hovered, boolean enabled, boolean primary) {
		boolean hover = enabled && hovered;
		int w = CARD_WIDTH;
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
		String shown = trim(label, w - 16);
		context.drawText(text, shown, x + (w - text.getWidth(shown)) / 2, y + 10, color, false);
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
