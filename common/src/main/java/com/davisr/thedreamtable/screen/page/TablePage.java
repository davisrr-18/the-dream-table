package com.davisr.thedreamtable.screen.page;

import com.davisr.thedreamtable.screen.ui.Adjustment;
import com.davisr.thedreamtable.screen.ui.ControlId;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

public interface TablePage {
	void draw(DrawContext context, int mouseX, int mouseY);

	boolean click(double mouseX, double mouseY);

	/** Called while the left button is held after a click this page handled, such as on a slider. */
	default boolean drag(double mouseX, double mouseY) {
		return false;
	}

	default boolean release(double mouseX, double mouseY) {
		return false;
	}

	/** A key pressed on the focused slider {@code control}; true when this page owns the slider and changed it. */
	default boolean adjust(ControlId control, Adjustment how) {
		return false;
	}

	/** Text a screen reader announces after {@link #adjust} changed a slider, such as its new value; empty for none. */
	default Text adjusted(ControlId control) {
		return Text.empty();
	}

	/** Called before the page is left or the screen closes, to send anything the page still holds back, such as a keyed value. */
	default void flush() {
	}

	int contentHeight();

	Text subtitle();
}
