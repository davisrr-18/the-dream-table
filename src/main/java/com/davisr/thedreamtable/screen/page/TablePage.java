package com.davisr.thedreamtable.screen.page;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

public interface TablePage {
	void draw(DrawContext context, int mouseX, int mouseY);

	boolean click(double mouseX, double mouseY);

	int contentHeight();

	Text subtitle();
}
