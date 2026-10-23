package com.davisr.thedreamtable.screen.page;

import com.davisr.thedreamtable.client.ClientSettings;
import com.davisr.thedreamtable.client.LoadedFarmsView;
import com.davisr.thedreamtable.client.OtherFarms;
import com.davisr.thedreamtable.farm.BuildPace;
import com.davisr.thedreamtable.screen.DreamTableScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import static com.davisr.thedreamtable.screen.ui.Theme.*;

/**
 * The options: Build Speed, which opens {@link SpeedPage}; Keep Farms Loaded, which opens {@link LoadedFarmsPage} to pick
 * which finished farms stay loaded; and the "Show Other Farms" switch, kept on this player's computer.
 */
public final class OptionsPage implements TablePage {
	private static final int SPEED = 0;
	private static final int LOADED = 1;
	private static final int OTHERS = 2;
	private static final int CARDS = 3;

	private final PageContext ui;

	public OptionsPage(PageContext ui) {
		this.ui = ui;
	}

	@Override
	public void draw(DrawContext context, int mouseX, int mouseY) {
		DreamTableScreenHandler handler = ui.handler();
		Text speed = Text.translatable("gui.the_dream_table.with_eta", SpeedPage.chosen(handler),
				Text.translatable("gui.the_dream_table.pace_share", BuildPace.percent(handler.getPace())));
		ui.card(context, new ItemStack(Items.CLOCK), Text.translatable("gui.the_dream_table.speed"), speed, ui.view().cardY(SPEED), mouseX, mouseY, false, true);
		Text loaded = handler.getLoadedLimit() <= 0
				? Text.translatable("gui.the_dream_table.loaded_off_short")
				: Text.translatable("gui.the_dream_table.loaded_count", handler.getLoadedFarms(), handler.getLoadedLimit());
		ui.card(context, new ItemStack(Items.LODESTONE), Text.translatable("gui.the_dream_table.loaded"), loaded, ui.view().cardY(LOADED), mouseX, mouseY,
				handler.getLoadedFarms() > 0, true);
		boolean shown = ClientSettings.showOtherFarms();
		Text others = Text.translatable(shown ? "gui.the_dream_table.show_others_on" : "gui.the_dream_table.show_others_off");
		ui.card(context, new ItemStack(Items.SPYGLASS), others, ui.view().cardY(OTHERS), mouseX, mouseY, shown, true);
		ui.button(context, Text.translatable("gui.the_dream_table.back"), backY(), mouseX, mouseY, true, false);
	}

	@Override
	public boolean click(double mouseX, double mouseY) {
		if (ui.cardHit(mouseX, mouseY, ui.view().cardY(SPEED))) {
			ui.go(DreamTableScreenHandler.PAGE_SPEED, DreamTableScreenHandler.BUTTON_SPEED_OPEN);
			return true;
		}
		if (ui.cardHit(mouseX, mouseY, ui.view().cardY(LOADED))) {
			LoadedFarmsView.invalidate();
			ui.go(DreamTableScreenHandler.PAGE_LOADED, DreamTableScreenHandler.BUTTON_LOADED_FARMS);
			return true;
		}
		if (ui.cardHit(mouseX, mouseY, ui.view().cardY(OTHERS))) {
			OtherFarms.setShown(!ClientSettings.showOtherFarms());
			ui.actions().click();
			return true;
		}
		if (ui.buttonHit(mouseX, mouseY, backY())) {
			ui.go(DreamTableScreenHandler.PAGE_SELECT, DreamTableScreenHandler.BUTTON_OPTIONS_BACK);
			return true;
		}
		return false;
	}

	@Override
	public int contentHeight() {
		return PAD + CARDS * CARD_HEIGHT + (CARDS - 1) * CARD_GAP + 6 + BUTTON_HEIGHT + 4;
	}

	@Override
	public Text subtitle() {
		return Text.translatable("gui.the_dream_table.options");
	}

	private int backY() {
		return ui.view().cardY(CARDS - 1) + CARD_HEIGHT + 6;
	}
}
