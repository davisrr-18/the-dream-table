package com.davisr.thedreamtable.screen.page;

import com.davisr.thedreamtable.farm.BuildPace;
import com.davisr.thedreamtable.farm.FarmOptions.FarmOption;
import com.davisr.thedreamtable.farm.FarmSize;
import com.davisr.thedreamtable.farm.FarmTemplate;
import com.davisr.thedreamtable.farm.FarmTemplates;
import com.davisr.thedreamtable.screen.DreamTableScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * A farm in a list: its name with a colored size tag, then its block count and how long it would take at the chosen speed,
 * like "Iron Farm   Medium" over "2505 blocks · ~20 s". Shared by the category lists and My Schematics.
 */
final class FarmCard {
	private static final int TINY_COLOR = 0xFF2F8A3B;
	private static final int MEDIUM_COLOR = 0xFF2667A8;
	private static final int BIG_COLOR = 0xFFB4541F;

	private FarmCard() {
	}

	static void draw(PageContext ui, DrawContext context, FarmOption option, int y, int mouseX, int mouseY, boolean selected, boolean enabled) {
		FarmTemplate template = FarmTemplates.get(option.id());
		if (template == null) {
			ui.card(context, option.icon().getDefaultStack(), option.title(), y, mouseX, mouseY, selected, enabled);
			return;
		}
		int steps = template.steps().size();
		FarmSize size = FarmSize.of(steps);
		ui.card(context, option.icon().getDefaultStack(), option.title(), detail(ui.handler(), steps), size.title(), color(size), y, mouseX, mouseY, selected, enabled);
	}

	private static Text detail(DreamTableScreenHandler handler, int steps) {
		int seconds = BuildPace.secondsLeftAt(handler.blocksPerSecondAt(handler.getPace()), steps, 0);
		return Text.translatable("gui.the_dream_table.with_eta", Text.translatable("gui.the_dream_table.farm_size", steps), TimeText.of(seconds));
	}

	static int color(FarmSize size) {
		return switch (size) {
			case TINY -> TINY_COLOR;
			case MEDIUM -> MEDIUM_COLOR;
			case BIG -> BIG_COLOR;
		};
	}
}
