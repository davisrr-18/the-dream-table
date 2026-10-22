package com.davisr.thedreamtable.screen.page;

import com.davisr.thedreamtable.farm.FarmOptions;
import com.davisr.thedreamtable.farm.FarmOptions.FarmOption;
import com.davisr.thedreamtable.screen.DreamTableScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import static com.davisr.thedreamtable.screen.ui.Theme.*;

public final class SelectionPage implements TablePage {
	private final PageContext ui;

	public SelectionPage(PageContext ui) {
		this.ui = ui;
	}

	@Override
	public void draw(DrawContext context, int mouseX, int mouseY) {
		boolean locked = ProjectState.locked(ui.handler());
		for (int index = 0; index < FarmOptions.ALL.size(); index++) {
			FarmOption option = FarmOptions.ALL.get(index);
			boolean selected = option.id().equals(ui.farm());
			ui.card(context, option.icon().getDefaultStack(), option.title(), ui.view().cardY(index), mouseX, mouseY, selected, !locked || selected);
		}
		ui.card(context, new ItemStack(Items.BOOK), Text.translatable("gui.the_dream_table.schemes"), schemesY(), mouseX, mouseY, false, true);
		ui.card(context, new ItemStack(Items.REPEATER), Text.translatable("gui.the_dream_table.options"), optionsY(), mouseX, mouseY, false, true);
	}

	@Override
	public boolean click(double mouseX, double mouseY) {
		for (int index = 0; index < FarmOptions.ALL.size(); index++) {
			if (ui.cardHit(mouseX, mouseY, ui.view().cardY(index))) {
				FarmOption option = FarmOptions.ALL.get(index);
				if (!option.id().equals(ui.farm()) && ProjectState.locked(ui.handler())) {
					ui.message("gui.the_dream_table.farm_busy");
					return true;
				}
				ui.go(option.id(), DreamTableScreenHandler.PAGE_WORK, index);
				return true;
			}
		}
		if (ui.cardHit(mouseX, mouseY, schemesY())) {
			ui.go(DreamTableScreenHandler.PAGE_SCHEMES, DreamTableScreenHandler.BUTTON_SCHEMES);
			return true;
		}
		if (ui.cardHit(mouseX, mouseY, optionsY())) {
			ui.go(DreamTableScreenHandler.PAGE_OPTIONS, DreamTableScreenHandler.BUTTON_OPTIONS);
			return true;
		}
		return false;
	}

	@Override
	public int contentHeight() {
		int count = FarmOptions.ALL.size() + 2;
		return PAD + count * CARD_HEIGHT + (count - 1) * CARD_GAP + 6;
	}

	@Override
	public Text subtitle() {
		return Text.translatable("gui.the_dream_table.choose");
	}

	private int schemesY() {
		return ui.view().cardY(FarmOptions.ALL.size());
	}

	private int optionsY() {
		return ui.view().cardY(FarmOptions.ALL.size() + 1);
	}
}
