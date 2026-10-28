package com.davisr.thedreamtable.screen.page;

import com.davisr.thedreamtable.client.ClientSettings;
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
	private static final int OTHERS = 1;
	private static final int TRACKER = 2;
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
		ui.cardAt("speed", context, new ItemStack(Items.CLOCK), Text.translatable("gui.the_dream_table.speed"), speed,
				ui.view().gridCardX(SPEED), ui.view().gridCardY(SPEED), mouseX, mouseY, false, true);
		boolean shown = ClientSettings.showOtherFarms();
		Text others = Text.translatable(shown ? "gui.the_dream_table.show_others_on" : "gui.the_dream_table.show_others_off");
		ui.cardAt("show_other_farms", context, new ItemStack(Items.SPYGLASS), others,
				ui.view().gridCardX(OTHERS), ui.view().gridCardY(OTHERS), mouseX, mouseY, shown, true);
		boolean tracker = ClientSettings.showTrackedFarm();
		ui.cardAt("show_tracked_farm", context, new ItemStack(Items.COMPASS),
				Text.translatable(tracker ? "gui.the_dream_table.show_tracker_on" : "gui.the_dream_table.show_tracker_off"),
				ui.view().gridCardX(TRACKER), ui.view().gridCardY(TRACKER), mouseX, mouseY, tracker, true);
	}

	@Override
	public boolean click(double mouseX, double mouseY) {
		if (ui.cardHit(mouseX, mouseY, ui.view().gridCardX(SPEED), ui.view().gridCardY(SPEED))) {
			ui.go(DreamTableScreenHandler.PAGE_SPEED, DreamTableScreenHandler.BUTTON_SPEED_OPEN);
			return true;
		}
		if (ui.cardHit(mouseX, mouseY, ui.view().gridCardX(OTHERS), ui.view().gridCardY(OTHERS))) {
			OtherFarms.setShown(!ClientSettings.showOtherFarms());
			ui.actions().click();
			return true;
		}
		if (ui.cardHit(mouseX, mouseY, ui.view().gridCardX(TRACKER), ui.view().gridCardY(TRACKER))) {
			ClientSettings.setShowTrackedFarm(!ClientSettings.showTrackedFarm());
			ui.actions().click();
			return true;
		}
		return false;
	}

	@Override
	public int contentHeight() {
		return ui.view().gridContentHeight(CARDS);
	}

	@Override
	public Text subtitle() {
		return Text.translatable("gui.the_dream_table.options");
	}

}
