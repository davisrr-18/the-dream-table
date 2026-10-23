package com.davisr.thedreamtable.screen.page;

import com.davisr.thedreamtable.farm.BuildPace;
import com.davisr.thedreamtable.farm.FarmOptions.FarmOption;
import com.davisr.thedreamtable.farm.FarmTemplates;
import com.davisr.thedreamtable.screen.DreamTableScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/** A farm in a list: its name over its block count and how long it would take at the chosen speed, like "2505 blocks · ~20 s". */
final class FarmCard {
	private FarmCard() {
	}

	static void draw(PageContext ui, DrawContext context, FarmOption option, int y, int mouseX, int mouseY, boolean selected, boolean enabled) {
		int steps = FarmTemplates.stepCount(option.id());
		if (steps < 0) {
			ui.card(context, option.icon().getDefaultStack(), option.title(), y, mouseX, mouseY, selected, enabled);
			return;
		}
		ui.card(context, option.icon().getDefaultStack(), option.title(), detail(ui.handler(), steps), y, mouseX, mouseY, selected, enabled);
	}

	private static Text detail(DreamTableScreenHandler handler, int steps) {
		int seconds = BuildPace.secondsLeftAt(handler.blocksPerSecondAt(handler.getPace()), steps, 0);
		return Text.translatable("gui.the_dream_table.with_eta", Text.translatable("gui.the_dream_table.farm_size", steps), TimeText.of(seconds));
	}
}
