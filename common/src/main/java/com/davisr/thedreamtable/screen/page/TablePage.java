package com.davisr.thedreamtable.screen.page;

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

	int contentHeight();

	Text subtitle();
}
