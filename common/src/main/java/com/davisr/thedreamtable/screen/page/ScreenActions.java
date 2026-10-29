package com.davisr.thedreamtable.screen.page;

import net.minecraft.text.Text;

/** What a page may ask the screen to do. */
public interface ScreenActions {
	void press(int buttonId);

	void click();

	/** A problem or refusal, shown as a red table notice. */
	void message(Text text);

	/** A neutral hint, such as "click again to confirm", shown as a green table notice. */
	void hint(Text text);

	void close();

	boolean creative();
}
