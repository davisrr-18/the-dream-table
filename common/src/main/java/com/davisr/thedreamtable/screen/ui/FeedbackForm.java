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
	private final Box field;
	private FeedbackCategory category = FeedbackCategory.BUG;

	public FeedbackForm(TextRenderer text, Text placeholder) {
		field = new Box(text, placeholder);
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
		field.setDimensionsAndPosition(width, height, x, y);
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

	private static final class Box extends EditBoxWidget {
		private boolean usable;

		Box(TextRenderer text, Text placeholder) {
			super(text, 0, 0, 1, 1, placeholder, Text.empty());
		}

		@Override
		public boolean mouseClicked(double mouseX, double mouseY, int button) {
			return ClickArea.hits(usable, bounds(), mouseX, mouseY) && super.mouseClicked(mouseX, mouseY, button);
		}

		@Override
		public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
			return ClickArea.hits(usable, bounds(), mouseX, mouseY) && super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
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
