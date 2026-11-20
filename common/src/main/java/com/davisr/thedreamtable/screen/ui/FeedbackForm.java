package com.davisr.thedreamtable.screen.ui;

import com.davisr.thedreamtable.client.feedback.FeedbackCategory;
import com.davisr.thedreamtable.client.feedback.FeedbackJson;
import com.davisr.thedreamtable.screen.ui.layout.ClickArea;
import com.davisr.thedreamtable.screen.ui.layout.UiRect;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.text.Text;

/**
 * The feedback text box and the chosen category. The box is a vanilla multi-line field, so it takes the typing, the
 * clipboard and the caret; the screen routes keys and focus to it while it has focus, and the page places it each frame.
 * Vanilla's own click handler ignores visibility, so the box only takes clicks, scrolls and drags while it is usable.
 */
public final class FeedbackForm {
	private final TextRenderer text;
	private final Text placeholder;
	private Box field;
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
		return field.usable;
	}

	public FeedbackCategory category() {
		return category;
	}

	public void choose(FeedbackCategory chosen) {
		category = chosen;
	}

	/** Places the box for this frame; {@code reachable} is false while part of it is scrolled out of view. */
	public void place(int x, int y, int width, int height, boolean reachable) {
		if (width != field.getWidth() || height != field.getHeight()) {
			rebuild(width, height);
		}
		field.setX(x);
		field.setY(y);
		field.visible = true;
		field.usable = reachable;
		field.active = reachable;
	}

	/** Hides the box until the page places it again; the screen calls this at the start of every frame. */
	public void hide() {
		field.visible = false;
		field.active = false;
		field.usable = false;
	}

	public void draw(DrawContext context, int mouseX, int mouseY) {
		field.render(context, mouseX, mouseY, 0F);
	}

	/**
	 * 1.20.1's EditBoxWidget wraps at the width it was built with, and the compiled 1.20.1 jar has no setHeight, so a new
	 * size needs a new box; the text, the focus and the length limit carry over.
	 */
	private void rebuild(int width, int height) {
		Box next = create(width, height);
		next.setText(field.getText());
		next.setFocused(field.isFocused());
		field = next;
	}

	private Box create(int width, int height) {
		Box created = new Box(text, placeholder, width, height);
		created.setMaxLength(FeedbackJson.MAX_LENGTH);
		return created;
	}

	private static final class Box extends EditBoxWidget {
		private boolean usable;

		Box(TextRenderer text, Text placeholder, int width, int height) {
			super(text, 0, 0, width, height, placeholder, Text.empty());
		}

		@Override
		public boolean mouseClicked(double mouseX, double mouseY, int button) {
			return ClickArea.hits(usable, bounds(), mouseX, mouseY) && super.mouseClicked(mouseX, mouseY, button);
		}

		@Override
		public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
			return ClickArea.hits(usable, bounds(), mouseX, mouseY) && super.mouseScrolled(mouseX, mouseY, amount);
		}

		@Override
		public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
			return usable && super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
		}

		private UiRect bounds() {
			return new UiRect(getX(), getY(), getWidth(), getHeight());
		}
	}
}
