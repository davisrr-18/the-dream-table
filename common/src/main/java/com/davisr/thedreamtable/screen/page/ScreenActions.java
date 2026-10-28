package com.davisr.thedreamtable.screen.page;

import net.minecraft.text.Text;

/** What a page may ask the screen to do. */
public interface ScreenActions {
	void press(int buttonId);

	void click();

	void message(Text text);

	void close();

	boolean creative();
}
