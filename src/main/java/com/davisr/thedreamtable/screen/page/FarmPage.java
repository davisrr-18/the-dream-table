package com.davisr.thedreamtable.screen.page;

import com.davisr.thedreamtable.client.LoadedFarmsView;
import com.davisr.thedreamtable.farm.FarmLoader;
import com.davisr.thedreamtable.farm.FarmTemplates;
import com.davisr.thedreamtable.screen.DreamTableScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import static com.davisr.thedreamtable.screen.ui.Theme.*;

/**
 * One finished farm from My Farms: where it is, whether it is running, and what to do with it. Keep Loaded switches it
 * on or off, Repair rebuilds only what is missing at this table, and Take Down, after a second click, removes it and
 * hands the blocks back. A farm the mod no longer has, or whose schematic is gone, can only be forgotten.
 */
public final class FarmPage implements TablePage {
	private static final int LOADED = 0;
	private static final int REPAIR = 1;
	private static final int TAKE_DOWN = 2;
	private static final int BACK = 3;
	private static final long CONFIRM_MS = 3000L;

	private final PageContext ui;
	private long confirmUntil;

	public FarmPage(PageContext ui) {
		this.ui = ui;
	}

	private FarmLoader.Entry farm() {
		int index = LoadedFarmsView.selectedIndex();
		return index < 0 ? null : LoadedFarmsView.farms().get(index);
	}

	@Override
	public void draw(DrawContext context, int mouseX, int mouseY) {
		LoadedFarmsView.ensure();
		FarmLoader.Entry farm = farm();
		if (farm == null) {
			context.drawText(ui.painter().text(), Text.translatable(LoadedFarmsView.received() ? "gui.the_dream_table.farm_gone" : "gui.the_dream_table.loaded_waiting"),
					ui.view().cardX(), ui.view().cardY(0) + 8, HINT, false);
			ui.button(context, Text.translatable("gui.the_dream_table.back"), rowY(BACK), mouseX, mouseY, true, false);
			return;
		}
		Text where = Text.translatable("gui.the_dream_table.farm_at", farm.where().getX(), farm.where().getY(), farm.where().getZ(),
				LoadedFarmsPage.dimension(farm.dimension()));
		LoadedFarmsPage.card(ui, context, farm, where, ui.view().cardY(0), mouseX, mouseY);
		Text status = farm.available() ? LoadedFarmsPage.status(farm.status()) : Text.translatable(FarmTemplates.isImported(farm.farmId())
				? "gui.the_dream_table.farm_file_missing" : "gui.the_dream_table.farm_removed");
		context.drawText(ui.painter().text(), status, ui.view().cardX(), statusY(), HINT, false);
		boolean canLoad = LoadedFarmsView.limit() > 0;
		Text loaded = Text.translatable(farm.loaded() ? "gui.the_dream_table.keep_loaded_on" : "gui.the_dream_table.keep_loaded_off");
		ui.button(context, loaded, rowY(LOADED), mouseX, mouseY, canLoad, farm.loaded());
		if (!farm.available()) {
			ui.button(context, Text.translatable("gui.the_dream_table.forget"), rowY(REPAIR), mouseX, mouseY, true, false);
		} else {
			ui.button(context, repairLabel(farm), rowY(REPAIR), mouseX, mouseY, farm.missing() != 0, false);
			Text takeDown = Text.translatable(confirming() ? "gui.the_dream_table.take_down_confirm" : "gui.the_dream_table.take_down");
			ui.button(context, takeDown, rowY(TAKE_DOWN), mouseX, mouseY, true, confirming());
		}
		ui.button(context, Text.translatable("gui.the_dream_table.back"), rowY(BACK), mouseX, mouseY, true, false);
	}

	@Override
	public boolean click(double mouseX, double mouseY) {
		FarmLoader.Entry farm = farm();
		int index = LoadedFarmsView.selectedIndex();
		if (ui.buttonHit(mouseX, mouseY, rowY(BACK))) {
			confirmUntil = 0L;
			ui.go(DreamTableScreenHandler.PAGE_LOADED, DreamTableScreenHandler.BUTTON_FARM_BACK);
			return true;
		}
		if (farm == null) {
			return false;
		}
		if (ui.buttonHit(mouseX, mouseY, rowY(LOADED)) && LoadedFarmsView.limit() > 0) {
			LoadedFarmsView.toggle(farm.key());
			ui.actions().click();
			return true;
		}
		if (!farm.available()) {
			if (ui.buttonHit(mouseX, mouseY, rowY(REPAIR))) {
				ui.actions().press(DreamTableScreenHandler.BUTTON_FARM_FORGET + index);
				ui.actions().click();
				ui.go(DreamTableScreenHandler.PAGE_LOADED, DreamTableScreenHandler.BUTTON_FARM_BACK);
				return true;
			}
			return false;
		}
		if (ui.buttonHit(mouseX, mouseY, rowY(REPAIR)) && farm.missing() != 0) {
			if (ProjectState.locked(ui.handler())) {
				ui.message("gui.the_dream_table.repair_busy");
				return true;
			}
			ui.handler().preview(farm.farmId(), DreamTableScreenHandler.PAGE_WORK);
			ui.actions().press(DreamTableScreenHandler.BUTTON_FARM_REPAIR + index);
			return true;
		}
		if (ui.buttonHit(mouseX, mouseY, rowY(TAKE_DOWN))) {
			if (!confirming()) {
				confirmUntil = Util.getMeasuringTimeMs() + CONFIRM_MS;
				ui.actions().click();
				ui.message("gui.the_dream_table.take_down_warning");
				return true;
			}
			confirmUntil = 0L;
			ui.actions().press(DreamTableScreenHandler.BUTTON_FARM_TAKE_DOWN + index);
			ui.go(DreamTableScreenHandler.PAGE_LOADED, DreamTableScreenHandler.BUTTON_FARM_BACK);
			return true;
		}
		return false;
	}

	/** "Repair · 12 missing", "Nothing To Repair" when the farm is whole, or just "Repair" while part of it is unloaded. */
	private static Text repairLabel(FarmLoader.Entry farm) {
		if (farm.missing() == 0) {
			return Text.translatable("gui.the_dream_table.repair_none");
		}
		if (farm.missing() == FarmLoader.UNKNOWN) {
			return Text.translatable("gui.the_dream_table.repair");
		}
		return Text.translatable("gui.the_dream_table.repair_missing", farm.missing());
	}

	private boolean confirming() {
		return Util.getMeasuringTimeMs() < confirmUntil;
	}

	@Override
	public int contentHeight() {
		return rowY(BACK) - ui.view().cardY(0) + PAD + BUTTON_HEIGHT + 4;
	}

	@Override
	public Text subtitle() {
		return Text.translatable("gui.the_dream_table.my_farms");
	}

	private int statusY() {
		return ui.view().cardY(0) + CARD_HEIGHT + 8;
	}

	private int rowY(int row) {
		return statusY() + 14 + row * (BUTTON_HEIGHT + BUTTON_GAP);
	}
}
