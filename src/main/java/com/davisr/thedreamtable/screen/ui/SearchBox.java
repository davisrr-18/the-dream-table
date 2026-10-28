package com.davisr.thedreamtable.screen.ui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiPredicate;

import static com.davisr.thedreamtable.screen.ui.Theme.*;

/** Takes the typing from a vanilla text field but draws its own shadowless text on the cream panel. */
public final class SearchBox {
	private static final int HEIGHT = 18;
	private static final int INSET = 6;

	private final TextFieldWidget field;
	private int page = -1;
	private Text hint = Text.translatable("gui.the_dream_table.search");

	public SearchBox(TextRenderer text, String initial, Runnable onChange) {
		field = new TextFieldWidget(text, 0, 0, CARD_WIDTH - INSET * 2, 12, Text.translatable("gui.the_dream_table.search"));
		field.setDrawsBackground(false);
		field.setMaxLength(48);
		field.setText(initial);
		field.setChangedListener(value -> onChange.run());
	}

	public TextFieldWidget widget() {
		return field;
	}

	public String text() {
		return field.getText();
	}

	public boolean visible() {
		return field.isVisible();
	}

	public boolean focused() {
		return field.isVisible() && field.isFocused();
	}

	public void setHint(Text hint) {
		this.hint = hint;
	}

	/**
	 * Shows the box on pages that search and clears it each time one is entered, unless {@code keep} says to keep the
	 * text from the page before. Returns true when it should take focus.
	 */
	public boolean sync(int currentPage, boolean shown, BiPredicate<Integer, Integer> keep) {
		if (currentPage == page) {
			return false;
		}
		if (page >= 0 && shown && !keep.test(page, currentPage)) {
			field.setText("");
		}
		page = currentPage;
		field.setVisible(shown);
		return shown;
	}

	public List<Integer> filter(List<Item> options) {
		String query = text().trim().toLowerCase(Locale.ROOT);
		List<Integer> shown = new ArrayList<>();
		for (int index = 0; index < options.size(); index++) {
			Item option = options.get(index);
			if (query.isEmpty()
					|| option.getName().getString().toLowerCase(Locale.ROOT).contains(query)
					|| Registries.ITEM.getId(option).getPath().contains(query.replace(' ', '_'))) {
				shown.add(index);
			}
		}
		return shown;
	}

	public void draw(DrawContext context, Painter painter, int x, int y) {
		if (!visible()) {
			return;
		}
		TextRenderer text = painter.text();
		painter.field(context, x, y, CARD_WIDTH, HEIGHT, field.isFocused());
		field.setX(x + INSET);
		field.setY(y + 5);

		int left = x + INSET;
		int top = y + 5;
		int room = CARD_WIDTH - INSET * 2;
		String value = field.getText();
		if (value.isEmpty()) {
			context.drawText(text, painter.trim(hint, room), left, top, MUTED, false);
		}
		int cursor = Math.max(0, Math.min(value.length(), field.getCursor()));
		String before = value.substring(0, cursor);
		int start = 0;
		if (text.getWidth(before) > room) {
			start = cursor - text.trimToWidth(before, room, true).length();
		}
		String shown = text.trimToWidth(value.substring(start), room);
		context.drawText(text, shown, left, top, NAME, false);

		boolean blink = (Util.getMeasuringTimeMs() / 300L) % 2L == 0L;
		if (field.isFocused() && blink) {
			int caret = left + text.getWidth(value.substring(start, cursor));
			context.fill(caret, top - 1, caret + 1, top + text.fontHeight, NAME);
		}
	}
}
