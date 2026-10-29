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
 * which finished farms stay loaded; and the "Show Other Farms", tracker and "Deliver from Nearby" switches, kept on this player's computer, and the "Protect My Farms" and "Viewers Can Break" switches, which the
 * server keeps because it enforces them.
 */
public final class OptionsPage implements TablePage {
	private static final int SPEED = 0;
	private static final int OTHERS = 1;
	private static final int TRACKER = 2;
	private static final int NEARBY = 3;
	private static final int PROTECT = 4;
	private static final int VIEWERS = 5;
	private static final int CARDS = 6;

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
		boolean serverAllows = handler.nearbyDeliveryAllowed();
		boolean nearby = serverAllows && ClientSettings.deliverFromNearby();
		ui.cardAt("deliver_nearby", context, new ItemStack(Items.CHEST),
				Text.translatable(nearby ? "gui.the_dream_table.deliver_nearby_on" : "gui.the_dream_table.deliver_nearby_off"),
				Text.translatable(serverAllows ? "gui.the_dream_table.deliver_nearby_hint" : "gui.the_dream_table.deliver_nearby_off_server"),
				ui.view().gridCardX(NEARBY), ui.view().gridCardY(NEARBY), mouseX, mouseY, nearby, serverAllows);
		int protection = handler.farmProtection();
		boolean protecting = protection == DreamTableScreenHandler.PROTECTION_ON;
		boolean protectionAllowed = protection != DreamTableScreenHandler.PROTECTION_OFF_SERVER;
		ui.cardAt("protect_farms", context, new ItemStack(Items.SHIELD),
				Text.translatable(protecting ? "gui.the_dream_table.protect_farms_on" : "gui.the_dream_table.protect_farms_off"),
				Text.translatable(protectionAllowed ? "gui.the_dream_table.protect_farms_hint" : "gui.the_dream_table.protect_farms_off_server"),
				ui.view().gridCardX(PROTECT), ui.view().gridCardY(PROTECT), mouseX, mouseY, protecting, protectionAllowed);
		boolean viewersAllowed = protecting;
		boolean viewers = viewersAllowed && handler.viewersMayBreak();
		ui.cardAt("viewers_break", context, new ItemStack(Items.ENDER_EYE),
				Text.translatable(viewers ? "gui.the_dream_table.viewers_break_on" : "gui.the_dream_table.viewers_break_off"),
				Text.translatable(viewersAllowed ? "gui.the_dream_table.viewers_break_hint" : viewersBreakReason(protection)),
				ui.view().gridCardX(VIEWERS), ui.view().gridCardY(VIEWERS), mouseX, mouseY, viewers, viewersAllowed);
	}

	/** Why "Viewers Can Break" can't be changed now: the server turned protection off, or the player has not turned it on. */
	private static String viewersBreakReason(int protection) {
		return protection == DreamTableScreenHandler.PROTECTION_OFF_SERVER
				? "gui.the_dream_table.protect_farms_off_server" : "gui.the_dream_table.viewers_break_needs_protection";
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
		if (ui.cardHit(mouseX, mouseY, ui.view().gridCardX(NEARBY), ui.view().gridCardY(NEARBY))) {
			if (!ui.handler().nearbyDeliveryAllowed()) {
				ui.message("gui.the_dream_table.deliver_nearby_off_server");
				return true;
			}
			ClientSettings.setDeliverFromNearby(!ClientSettings.deliverFromNearby());
			ui.actions().click();
			return true;
		}
		if (ui.cardHit(mouseX, mouseY, ui.view().gridCardX(PROTECT), ui.view().gridCardY(PROTECT))) {
			if (ui.handler().farmProtection() == DreamTableScreenHandler.PROTECTION_OFF_SERVER) {
				ui.message("gui.the_dream_table.protect_farms_off_server");
				return true;
			}
			ui.actions().press(DreamTableScreenHandler.BUTTON_PROTECT_FARMS);
			return true;
		}
		if (ui.cardHit(mouseX, mouseY, ui.view().gridCardX(VIEWERS), ui.view().gridCardY(VIEWERS))) {
			int protection = ui.handler().farmProtection();
			if (protection != DreamTableScreenHandler.PROTECTION_ON) {
				ui.message(viewersBreakReason(protection));
				return true;
			}
			ui.actions().press(DreamTableScreenHandler.BUTTON_VIEWERS_BREAK);
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
