package com.davisr.thedreamtable.screen.ui;

import com.davisr.thedreamtable.client.feedback.FeedbackCategory;
import com.davisr.thedreamtable.client.feedback.FeedbackJson;
import com.davisr.thedreamtable.screen.ui.layout.ClickArea;
import com.davisr.thedreamtable.screen.ui.layout.UiRect;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.client.gui.widget.ScrollableWidget;
import net.minecraft.text.Text;

/**
 * The feedback text box and the chosen category. The box is a vanilla multi-line field held by composition (1.21.9+
 * hides its constructor, so it cannot be subclassed), so it takes the typing, the clipboard and the caret; the screen
 * routes keys and focus to it while it has focus, and the page places it each frame. A box that is not usable is
 * inactive, which makes vanilla skip it for clicks, scrolls and its scrollbar; the screen gates drags with {@link #takes}.
 * The vanilla box wraps its text at the width it was built with, so a change of width builds a new box with the same text.
 */
public final class FeedbackForm {
	private final TextRenderer text;
	private final Text placeholder;
	private EditBoxWidget field;
	private boolean usable;
	private FeedbackCategory category = FeedbackCategory.BUG;

	public FeedbackForm(TextRenderer text, Text placeholder) {
		this.text = text;
		this.placeholder = placeholder;
		field = build(1, 1, "");
		hide();
	}

	/** The current box; it is replaced when the page gives it another width, so do not keep it across frames. */
	public EditBoxWidget widget() {
		return field;
	}

	public String text() {
		return field.getText();
	}

	public void clear() {
		field.setText("");
	}

	/** Clears the box only if the player has not typed since {@code sent} was taken, so text written during a send survives. */
	public void clearIfUnchanged(String sent) {
		if (text().equals(sent)) {
			clear();
		}
	}

	public boolean focused() {
		return field.isFocused();
	}

	/** Whether the box is shown and all of it is in view; only then it draws and takes keys and clicks. */
	public boolean usable() {
		return usable;
	}

	/** Whether a click or drag at this point belongs to the box: it is usable and the point is in it or its scrollbar. */
	public boolean takes(double mouseX, double mouseY) {
		UiRect bounds = new UiRect(field.getX(), field.getY(), field.getWidth() + ScrollableWidget.SCROLLBAR_WIDTH, field.getHeight());
		return ClickArea.hits(usable, bounds, mouseX, mouseY);
	}

	public FeedbackCategory category() {
		return category;
	}

	public void choose(FeedbackCategory chosen) {
		category = chosen;
	}

	/** Places the box for this frame; {@code reachable} is false while part of it is scrolled out of view. */
	public void place(int x, int y, int width, int height, boolean reachable) {
		if (width != field.getWidth()) {
			field = build(width, height, field.getText());
		}
		field.setDimensionsAndPosition(width, height, x, y);
		field.visible = true;
		usable = reachable;
		field.active = reachable;
	}

	/** Hides the box until the page places it again; the screen calls this at the start of every frame. */
	public void hide() {
		field.visible = false;
		field.active = false;
		usable = false;
	}

	public void draw(DrawContext context, int mouseX, int mouseY) {
		field.render(context, mouseX, mouseY, 0F);
	}

	private EditBoxWidget build(int width, int height, String content) {
		EditBoxWidget box = EditBoxWidget.builder().placeholder(placeholder).build(text, width, height, Text.empty());
		box.setMaxLength(FeedbackJson.MAX_LENGTH);
		box.setText(content);
		return box;
	}
}
