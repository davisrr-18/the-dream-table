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
	private final TextRenderer text;
	private final Text placeholder;
	private EditBoxWidget field;
	private FeedbackCategory category = FeedbackCategory.BUG;

	public FeedbackForm(TextRenderer text, Text placeholder) {
		this.text = text;
		this.placeholder = placeholder;
		field = create(1, 1);
		hide();
	}

	/** The current box. place() can replace it when the size changes (1.20.1 only), so the screen re-registers it after each page draw. */
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
		if (width != field.getWidth() || height != field.getHeight()) {
			rebuild(width, height);
		}
		field.setX(x);
		field.setY(y);
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

	/**
	 * 1.20.1's EditBoxWidget wraps at the width it was built with, and the compiled 1.20.1 jar has no setHeight, so a new
	 * size needs a new box; the text, the focus and the length limit carry over.
	 */
	private void rebuild(int width, int height) {
		EditBoxWidget next = create(width, height);
		next.setText(field.getText());
		next.setFocused(field.isFocused());
		field = next;
	}

	private EditBoxWidget create(int width, int height) {
		EditBoxWidget created = new EditBoxWidget(text, 0, 0, width, height, placeholder, Text.empty());
		created.setMaxLength(FeedbackJson.MAX_LENGTH);
		return created;
	}
}
