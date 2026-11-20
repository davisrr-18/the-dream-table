package com.davisr.thedreamtable.screen.ui;

import com.davisr.thedreamtable.client.feedback.FeedbackCategory;
import com.davisr.thedreamtable.client.feedback.FeedbackJson;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.text.Text;

/**
 * The feedback text box and the chosen category. The box is a vanilla multi-line field, so it takes the typing, the
 * clipboard and the caret; the screen routes keys and focus to it while it has focus, and the page places it each frame.
 */
public final class FeedbackForm {
	private final EditBoxWidget field;
	private FeedbackCategory category = FeedbackCategory.BUG;

	public FeedbackForm(TextRenderer text, Text placeholder) {
		field = new EditBoxWidget(text, 0, 0, 1, 1, placeholder, Text.empty());
		field.setMaxLength(FeedbackJson.MAX_LENGTH);
		hide();
	}

	public EditBoxWidget widget() {
		return field;
	}

	public String text() {
		return field.getText();
	}

	public void clear() {
		field.setText("");
	}

	public boolean focused() {
		return field.isFocused();
	}

	public boolean shown() {
		return field.visible;
	}

	public FeedbackCategory category() {
		return category;
	}

	public void choose(FeedbackCategory chosen) {
		category = chosen;
	}

	/** Places the box for this frame; it takes clicks only while all of it is in view. */
	public void place(int x, int y, int width, int height, boolean reachable) {
		field.setDimensionsAndPosition(width, height, x, y);
		field.visible = true;
		field.active = reachable;
	}

	/** Hides the box until the page places it again; the screen calls this at the start of every frame. */
	public void hide() {
		field.visible = false;
		field.active = false;
	}

	public void draw(DrawContext context, int mouseX, int mouseY) {
		field.render(context, mouseX, mouseY, 0F);
	}
}
