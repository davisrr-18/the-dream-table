package com.davisr.thedreamtable.screen.page;

import com.davisr.thedreamtable.farm.BuildPace;
import com.davisr.thedreamtable.screen.DreamTableScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import static com.davisr.thedreamtable.screen.ui.Theme.*;

/** The pace list: "finish in about N minutes", which scales the speed with the farm's size. */
public final class OptionsPage implements TablePage {
	private static final Choice[] CHOICES = {
			new Choice(BuildPace.ofMinutes(10), Items.CLOCK, "gui.the_dream_table.pace_10", DreamTableScreenHandler.BUTTON_PACE_MINUTES + 10),
			new Choice(BuildPace.ofMinutes(5), Items.REPEATER, "gui.the_dream_table.pace_5", DreamTableScreenHandler.BUTTON_PACE_MINUTES + 5),
			new Choice(BuildPace.ofMinutes(1), Items.REDSTONE_BLOCK, "gui.the_dream_table.pace_1", DreamTableScreenHandler.BUTTON_PACE_MINUTES + 1),
	};

	private final PageContext ui;

	public OptionsPage(PageContext ui) {
		this.ui = ui;
	}

	private record Choice(int pace, Item icon, String label, int button) {
	}

	@Override
	public void draw(DrawContext context, int mouseX, int mouseY) {
		int pace = ui.handler().getPace();
		for (int index = 0; index < CHOICES.length; index++) {
			Choice choice = CHOICES[index];
			ui.card(context, new ItemStack(choice.icon()), Text.translatable(choice.label()), ui.view().cardY(index), mouseX, mouseY, pace == choice.pace(), true);
		}
		ui.button(context, Text.translatable("gui.the_dream_table.back"), backY(), mouseX, mouseY, true, false);
	}

	@Override
	public boolean click(double mouseX, double mouseY) {
		for (int index = 0; index < CHOICES.length; index++) {
			if (ui.cardHit(mouseX, mouseY, ui.view().cardY(index))) {
				ui.handler().previewPace(CHOICES[index].pace());
				ui.actions().press(CHOICES[index].button());
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
		return PAD + CHOICES.length * CARD_HEIGHT + (CHOICES.length - 1) * CARD_GAP + 6 + BUTTON_HEIGHT + 4;
	}

	@Override
	public Text subtitle() {
		return Text.translatable("gui.the_dream_table.speed");
	}

	private int backY() {
		return ui.view().cardY(CHOICES.length - 1) + CARD_HEIGHT + 6;
	}
}
