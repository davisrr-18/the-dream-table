package com.davisr.thedreamtable.screen.page;

import com.davisr.thedreamtable.screen.DreamTableScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import static com.davisr.thedreamtable.screen.ui.Theme.*;

public final class OptionsPage implements TablePage {
	private static final int[] SPEEDS = {1, 2, 4, 8};
	private static final Item[] ICONS = {Items.CLOCK, Items.REPEATER, Items.COMPARATOR, Items.REDSTONE_BLOCK};

	private final PageContext ui;

	public OptionsPage(PageContext ui) {
		this.ui = ui;
	}

	@Override
	public void draw(DrawContext context, int mouseX, int mouseY) {
		int speed = ui.handler().getBlocksPerSecond();
		for (int index = 0; index < SPEEDS.length; index++) {
			Text label = Text.translatable("gui.the_dream_table.speed_" + SPEEDS[index]);
			ui.card(context, new ItemStack(ICONS[index]), label, ui.view().cardY(index), mouseX, mouseY, speed == SPEEDS[index], true);
		}
		ui.button(context, Text.translatable("gui.the_dream_table.back"), backY(), mouseX, mouseY, true, false);
	}

	@Override
	public boolean click(double mouseX, double mouseY) {
		for (int index = 0; index < SPEEDS.length; index++) {
			if (ui.cardHit(mouseX, mouseY, ui.view().cardY(index))) {
				ui.handler().previewSpeed(SPEEDS[index]);
				ui.actions().press(DreamTableScreenHandler.BUTTON_SPEED + SPEEDS[index]);
				return true;
			}
		}
		if (ui.buttonHit(mouseX, mouseY, backY())) {
			ui.go(DreamTableScreenHandler.PAGE_SELECT, DreamTableScreenHandler.BUTTON_OPTIONS_BACK);
			return true;
		}
		return false;
	}

	@Override
	public int contentHeight() {
		return PAD + SPEEDS.length * CARD_HEIGHT + (SPEEDS.length - 1) * CARD_GAP + 6 + BUTTON_HEIGHT + 4;
	}

	@Override
	public Text subtitle() {
		return Text.translatable("gui.the_dream_table.speed");
	}

	private int backY() {
		return ui.view().cardY(SPEEDS.length - 1) + CARD_HEIGHT + 6;
	}
}
