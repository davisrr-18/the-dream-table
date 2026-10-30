package com.davisr.thedreamtable.screen.page;

import com.davisr.thedreamtable.client.LoadedFarmsView;
import com.davisr.thedreamtable.farm.FarmListing;
import com.davisr.thedreamtable.farm.FarmLoader;
import com.davisr.thedreamtable.farm.FarmTemplates;
import com.davisr.thedreamtable.network.FarmAlertsPayload;
import com.davisr.thedreamtable.network.FarmCarePayload;
import com.davisr.thedreamtable.network.FarmDetailsPayload;
import com.davisr.thedreamtable.network.FarmPreferencePayload;
import com.davisr.thedreamtable.screen.DreamTableScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.util.List;
import java.util.Locale;

import static com.davisr.thedreamtable.screen.ui.Theme.*;

/**
 * One finished farm from My Farms: where it is, whether it is running, its storage, and its condition. Keep Loaded
 * switches it on or off, Repair rebuilds only what is missing at this table, showing first what that takes, and Take
 * Down, after a second click that names the farm, removes it and hands the blocks back. A farm the mod no longer has
 * can only be forgotten.
 */
public final class FarmPage implements TablePage {
	private static final int FAVORITE = 0;
	private static final int TRACK = 1;
	private static final int LOADED = 2;
	private static final int REPAIR = 3;
	private static final int TAKE_DOWN = 4;
	private static final long CONFIRM_MS = 3000L;
	private static final int ITEM_SPACING = 21;
	private static final int ITEM_SIZE = 16;
	private static final int ITEM_ROW_HEIGHT = 28;
	private static final int LINE_HEIGHT = 10;
	/** Room under the condition line for the items a repair takes, or the four lines of the Take Down warning. */
	private static final int NEEDS_HEIGHT = 40;

	private final PageContext ui;
	private long confirmUntil;
	private String acknowledged = "";
	private Layout layout;

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
		if (farm != null) {
			LoadedFarmsView.ensureDetails();
		}
		syncAlerts(farm);
		if (farm == null) {
			ui.flowNote(context, Text.translatable(LoadedFarmsView.received() ? "gui.the_dream_table.farm_gone" : "gui.the_dream_table.loaded_waiting"),
					ui.view().cardY(0) + 8, HINT);
			ui.buttonAt("back", context, Text.translatable("gui.the_dream_table.back"), flowX(), rowY(backRow(null)), mouseX, mouseY, true, false);
			return;
		}
		Text where = Text.translatable("gui.the_dream_table.farm_at", farm.where().getX(), farm.where().getY(), farm.where().getZ(),
				LoadedFarmsPage.dimension(farm.dimension()));
		LoadedFarmsPage.card(ui, context, farm, where, flowX(), ui.view().cardY(0), ui.view().flowWidth(), mouseX, mouseY);
		drawWrapped(context, summaryText(farm), statusY(), Integer.MAX_VALUE, HINT);
		drawStorage(context, mouseX, mouseY);
		drawCondition(context, farm, mouseX, mouseY);
		drawAlerts(context, farm);
		ui.buttonAt("favorite", context, Text.translatable(farm.favorite() ? "gui.the_dream_table.unfavorite" : "gui.the_dream_table.favorite"),
				flowX(), rowY(FAVORITE), mouseX, mouseY, true, farm.favorite());
		ui.buttonAt("track", context, Text.translatable(farm.tracked() ? "gui.the_dream_table.untrack" : "gui.the_dream_table.track"),
				flowX(), rowY(TRACK), mouseX, mouseY, true, farm.tracked());
		Text loadedReason = keepLoadedReason(LoadedFarmsView.limit(), farm.loaded(), farm.ownerQuotaFull());
		reasonButton("keep_loaded", context, loadedLabel(farm), loadedReason, LOADED, mouseX, mouseY, farm.loaded());
		if (farm.available()) {
			boolean busy = ProjectState.locked(ui.handler());
			reasonButton("repair", context, busy ? Text.translatable("gui.the_dream_table.repair_table_busy") : repairLabel(farm),
					repairReason(busy, farm.missing()), REPAIR, mouseX, mouseY, false);
			if (confirming()) {
				ui.buttonAt("cancel_take_down", context, Text.translatable("gui.the_dream_table.cancel"), flowX(), rowY(takeDownRow(farm)), mouseX, mouseY, true, false);
				ui.destructiveButtonAt("confirm_take_down", context, Text.translatable("gui.the_dream_table.confirm_take_down"), flowX(), rowY(confirmRow()), mouseX, mouseY, true);
			} else {
				ui.destructiveButtonAt("take_down", context, Text.translatable("gui.the_dream_table.take_down"), flowX(), rowY(takeDownRow(farm)), mouseX, mouseY, true);
			}
		} else {
			ui.destructiveButtonAt("forget", context, Text.translatable("gui.the_dream_table.forget"), flowX(), rowY(takeDownRow(farm)), mouseX, mouseY, true);
		}
		ui.buttonAt("back", context, Text.translatable("gui.the_dream_table.back"), flowX(), rowY(backRow(farm)), mouseX, mouseY, true, false);
	}

	@Override
	public boolean click(double mouseX, double mouseY) {
		FarmLoader.Entry farm = farm();
		if (ui.buttonHit(mouseX, mouseY, flowX(), rowY(backRow(farm)))) {
			confirmUntil = 0L;
			ui.go(DreamTableScreenHandler.PAGE_LOADED, DreamTableScreenHandler.BUTTON_FARM_BACK);
			return true;
		}
		if (farm == null) {
			return false;
		}
		if (ui.buttonHit(mouseX, mouseY, flowX(), rowY(FAVORITE))) {
			LoadedFarmsView.preference(farm.key(), farm.favorite() ? FarmPreferencePayload.Operation.UNFAVORITE : FarmPreferencePayload.Operation.FAVORITE);
			return true;
		}
		if (ui.buttonHit(mouseX, mouseY, flowX(), rowY(TRACK))) {
			LoadedFarmsView.preference(farm.key(), farm.tracked() ? FarmPreferencePayload.Operation.UNTRACK : FarmPreferencePayload.Operation.TRACK);
			return true;
		}
		if (ui.buttonHit(mouseX, mouseY, flowX(), rowY(LOADED)) && LoadedFarmsView.limit() > 0) {
			if (!canLoad(farm)) {
				ui.actions().message(Text.translatable("gui.the_dream_table.loaded_full", LoadedFarmsView.limit()));
				return true;
			}
			LoadedFarmsView.toggle(farm.key());
			ui.actions().click();
			return true;
		}
		if (farm.available() && ui.buttonHit(mouseX, mouseY, flowX(), rowY(REPAIR)) && farm.missing() != 0) {
			if (ProjectState.locked(ui.handler())) {
				ui.message("gui.the_dream_table.repair_busy");
				return true;
			}
			ui.handler().preview(farm.designId(), DreamTableScreenHandler.PAGE_WORK);
			LoadedFarmsView.care(ui.handler().getTablePos(), farm.key(), FarmCarePayload.Action.REPAIR);
			ui.actions().click();
			return true;
		}
		if (!farm.available()) {
			if (ui.buttonHit(mouseX, mouseY, flowX(), rowY(takeDownRow(farm)))) {
				LoadedFarmsView.care(ui.handler().getTablePos(), farm.key(), FarmCarePayload.Action.FORGET);
				ui.actions().click();
				ui.go(DreamTableScreenHandler.PAGE_LOADED, DreamTableScreenHandler.BUTTON_FARM_BACK);
				return true;
			}
			return false;
		}
		if (ui.buttonHit(mouseX, mouseY, flowX(), rowY(takeDownRow(farm)))) {
			if (confirming()) {
				confirmUntil = 0L;
				ui.actions().click();
				return true;
			} else {
				confirmUntil = Util.getMeasuringTimeMs() + CONFIRM_MS;
				ui.actions().click();
				ui.hint("gui.the_dream_table.take_down_click", LoadedFarmsPage.name(farm));
			}
			return true;
		}
		if (confirming() && ui.buttonHit(mouseX, mouseY, flowX(), rowY(confirmRow()))) {
			confirmUntil = 0L;
			LoadedFarmsView.care(ui.handler().getTablePos(), farm.key(), FarmCarePayload.Action.TAKE_DOWN);
			ui.actions().click();
			ui.go(DreamTableScreenHandler.PAGE_LOADED, DreamTableScreenHandler.BUTTON_FARM_BACK);
			return true;
		}
		return false;
	}

	/** Storage use, the items held with their counts, and the farm's block count, from the latest {@link FarmDetailsPayload}. */
	private void drawStorage(DrawContext context, int mouseX, int mouseY) {
		FarmDetailsPayload details = LoadedFarmsView.details();
		drawWrapped(context, storageText(details), storageY(), Integer.MAX_VALUE, NAME);
		if (details != null) {
			drawItems(context, details.items(), details.counts(), itemsY(), "gui.the_dream_table.item_stored", mouseX, mouseY);
			drawWrapped(context, infoText(details), blocksY(), Integer.MAX_VALUE, HINT);
		}
	}

	/** Operating state, Keep Loaded state, and distance or dimension for the selected farm. */
	private static Text summaryText(FarmLoader.Entry farm) {
		return summaryText(farm, LoadedFarmsView.viewer());
	}

	static Text summaryText(FarmLoader.Entry farm, FarmListing.Viewer viewer) {
		Text state = farm.available() ? LoadedFarmsPage.state(farm) : Text.translatable(FarmTemplates.isImported(farm.farmId())
				? "gui.the_dream_table.farm_file_missing" : "gui.the_dream_table.farm_removed");
		Text loadedState = Text.translatable(farm.loaded() ? "gui.the_dream_table.kept_loaded" : "gui.the_dream_table.not_kept_loaded");
		int away = FarmListing.blocksAway(farm, viewer);
		Text location = away >= 0 ? Text.translatable("gui.the_dream_table.blocks_away", away) : LoadedFarmsPage.dimension(farm.dimension());
		Text operating = Text.translatable("gui.the_dream_table.joined", loadedState, location);
		return Text.translatable("gui.the_dream_table.joined", state, operating);
	}

	private static Text storageText(FarmDetailsPayload details) {
		if (details == null) {
			return Text.translatable("gui.the_dream_table.loaded_waiting");
		}
		if (!details.loaded()) {
			return Text.translatable("gui.the_dream_table.storage_unloaded");
		}
		if (details.containers() == 0) {
			return Text.translatable("gui.the_dream_table.storage_none");
		}
		int percent = details.capacity() == 0 ? 0 : details.stored() * 100 / details.capacity();
		return Text.translatable("gui.the_dream_table.storage_use", details.stored(), details.capacity(), percent);
	}

	private static Text infoText(FarmDetailsPayload details) {
		return details.containers() > 0
				? Text.translatable("gui.the_dream_table.farm_info", details.blocks(), details.containers())
				: Text.translatable("gui.the_dream_table.farm_size", details.blocks());
	}

	/** Lines the text takes when wrapped to the page, at least one so an empty spot keeps its room. */
	private int lines(Text text) {
		return text == null ? 1 : Math.max(1, ui.painter().text().wrapLines(text, ui.view().flowWidth()).size());
	}

	/**
	 * How much of the farm stands: blocks missing, which Repair places, and blocks the player changed, which it leaves.
	 * Below, the items the missing blocks take, or, while Take Down waits for its second click, what it will do.
	 */
	private void drawCondition(DrawContext context, FarmLoader.Entry farm, int mouseX, int mouseY) {
		FarmDetailsPayload details = LoadedFarmsView.details();
		if (details == null || !farm.available()) {
			return;
		}
		drawWrapped(context, condition(details), conditionY(), Integer.MAX_VALUE, HINT);
		if (confirming()) {
			int y = needsY();
			int bottom = needsY() + NEEDS_HEIGHT;
			y = drawWrapped(context, takeDownWarning(farm), y, bottom, NAME);
			drawWrapped(context, Text.translatable("gui.the_dream_table.take_down_returns"), y, bottom, HINT);
			return;
		}
		drawItems(context, details.needs(), details.needCounts(), needsY(), "gui.the_dream_table.item_needed", mouseX, mouseY);
	}

	/** The farm's active alerts, in red, as the last lines of its description. */
	private void drawAlerts(DrawContext context, FarmLoader.Entry farm) {
		int y = alertsY();
		for (Text alert : AlertText.lines(farm, LoadedFarmsView.alerts())) {
			y = drawWrapped(context, alert, y, Integer.MAX_VALUE, ERROR);
		}
	}

	/**
	 * Asks for the farm's alert page when the list says it has an alert, and acknowledges the events of the page once it
	 * arrives and is drawn. Done by {@link #draw} only, never by the height math.
	 */
	private void syncAlerts(FarmLoader.Entry farm) {
		if (farm == null || farm.alerts() <= 0) {
			return;
		}
		LoadedFarmsView.ensureAlerts();
		FarmAlertsPayload page = LoadedFarmsView.alerts();
		if (page != null && !acknowledged.equals(page.token())) {
			LoadedFarmsView.acknowledgeAlerts(page);
			acknowledged = page.token();
		}
	}

	private static Text condition(FarmDetailsPayload details) {
		if (details.missing() == FarmLoader.UNKNOWN) {
			return Text.translatable("gui.the_dream_table.condition_unloaded");
		}
		if (details.missing() > 0 && details.changed() > 0) {
			return Text.translatable("gui.the_dream_table.condition_both", details.missing(), details.changed());
		}
		if (details.missing() > 0) {
			return Text.translatable("gui.the_dream_table.condition_missing", details.missing());
		}
		if (details.changed() > 0) {
			return Text.translatable("gui.the_dream_table.condition_changed", details.changed());
		}
		return Text.translatable("gui.the_dream_table.condition_whole");
	}

	/** Draws the text wrapped from y, stopping before bottom, and returns where the next line goes. */
	private int drawWrapped(DrawContext context, Text text, int y, int bottom, int color) {
		for (OrderedText line : ui.painter().text().wrapLines(text, ui.view().flowWidth())) {
			if (y + LINE_HEIGHT > bottom) {
				break;
			}
			context.drawText(ui.painter().text(), line, flowX(), y, color, false);
			y += LINE_HEIGHT;
		}
		return y;
	}

	/** Names the farm and where it is, so the second click can't hit the wrong one. */
	private static Text takeDownWarning(FarmLoader.Entry farm) {
		return Text.translatable("gui.the_dream_table.take_down_warning", LoadedFarmsPage.name(farm), farm.where().getX(), farm.where().getY(), farm.where().getZ());
	}

	/** Draws the icons with their short counts; hovering one shows the item's name and the exact count under {@code countKey}. */
	private void drawItems(DrawContext context, List<String> items, List<Integer> counts, int y, String countKey, int mouseX, int mouseY) {
		int x = flowX();
		for (int index = 0; index < items.size(); index++) {
			Identifier id = Identifier.tryParse(items.get(index));
			if (id == null || !Registries.ITEM.containsId(id)) {
				continue;
			}
			int itemX = x + itemXOffset(index);
			int itemY = y + itemRow(index) * ITEM_ROW_HEIGHT;
			ItemStack stack = new ItemStack(Registries.ITEM.get(id));
			context.drawItem(stack, itemX, itemY);
			context.drawText(ui.painter().text(), shortCount(counts.get(index)), itemX, itemY + 17, HINT, false);
			ui.hoverItem(stack, Text.translatable(countKey, String.format(Locale.ROOT, "%,d", counts.get(index))).formatted(Formatting.GRAY), itemX, itemY, mouseX, mouseY);
		}
	}

	static int itemXOffset(int index) {
		return index % itemColumns() * ITEM_SPACING;
	}

	static int itemRow(int index) {
		return index / itemColumns();
	}

	static int itemRows(int itemCount) {
		return itemCount == 0 ? 0 : (itemCount - 1) / itemColumns() + 1;
	}

	private static int itemColumns() {
		return (CARD_WIDTH - ITEM_SIZE) / ITEM_SPACING + 1;
	}

	private static int itemHeight(List<String> items) {
		return Math.max(ITEM_ROW_HEIGHT, itemRows(items.size()) * ITEM_ROW_HEIGHT);
	}

	/** "64", "1.2k": short enough to sit under a 16 pixel icon. */
	private static String shortCount(int count) {
		return count < 1000 ? Integer.toString(count) : count < 10000 ? String.format(Locale.ROOT, "%.1fk", count / 1000.0) : count / 1000 + "k";
	}

	/** Off when the server turned keeping farms loaded off, or when the limit is reached and this farm isn't one of them. */
	private static boolean canLoad(FarmLoader.Entry farm) {
		return LoadedFarmsView.limit() > 0 && (farm.loaded() || !farm.ownerQuotaFull());
	}

	/**
	 * A button that, when it can't be used, says why on its second line. {@code reason} null means it can be used.
	 * {@code active} marks a toggle that is on.
	 */
	private void reasonButton(String key, DrawContext context, Text label, Text reason, int row, int mouseX, int mouseY, boolean active) {
		if (reason == null) {
			ui.buttonAt(key, context, label, flowX(), rowY(row), mouseX, mouseY, true, active);
		} else {
			ui.buttonAt(key, context, label, reason, flowX(), rowY(row), mouseX, mouseY, false, active);
		}
	}

	/** Why Keep Loaded can't be changed, or null when it can. */
	static Text keepLoadedReason(int limit, boolean loaded, boolean ownerQuotaFull) {
		if (limit <= 0) {
			return Text.translatable("gui.the_dream_table.reason_keep_loaded_server");
		}
		return !loaded && ownerQuotaFull ? Text.translatable("gui.the_dream_table.loaded_full", limit) : null;
	}

	/** Why Repair is off, or null when it can be used: not while the table is busy, not for a whole farm. */
	static Text repairReason(boolean tableBusy, int missing) {
		if (tableBusy) {
			return Text.translatable("gui.the_dream_table.repair_busy");
		}
		return missing == 0 ? Text.translatable("gui.the_dream_table.reason_repair_whole") : null;
	}

	private static Text loadedLabel(FarmLoader.Entry farm) {
		if (farm.loaded()) {
			return Text.translatable("gui.the_dream_table.keep_loaded_on");
		}
		if (LoadedFarmsView.limit() <= 0) {
			return Text.translatable("gui.the_dream_table.keep_loaded_disabled");
		}
		return Text.translatable(canLoad(farm) ? "gui.the_dream_table.keep_loaded_off" : "gui.the_dream_table.keep_loaded_limit");
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
		return rowY(backRow(farm())) - ui.view().cardY(0) + PAD + BUTTON_HEIGHT + 4;
	}

	@Override
	public Text subtitle() {
		return Text.translatable("gui.the_dream_table.my_farms");
	}

	private int statusY() {
		return ui.view().cardY(0) + layout().status;
	}

	private int flowX() {
		return ui.view().flowX();
	}

	private int storageY() {
		return ui.view().cardY(0) + layout().storage;
	}

	private int itemsY() {
		return ui.view().cardY(0) + layout().items;
	}

	private int blocksY() {
		return ui.view().cardY(0) + layout().blocks;
	}

	private int conditionY() {
		return ui.view().cardY(0) + layout().condition;
	}

	private int needsY() {
		return ui.view().cardY(0) + layout().needs;
	}

	/** Under everything else the farm shows, so a farm without an alert looks as it did before. */
	private int alertsY() {
		return ui.view().cardY(0) + layout().alerts;
	}

	private int rowY(int row) {
		return ui.view().cardY(0) + layout().rows + row * (BUTTON_HEIGHT + BUTTON_GAP);
	}

	/**
	 * Where each text block below the card starts, relative to the card's top. Each block wraps to as many lines as it
	 * needs and what follows moves down with it, so the offsets are worked out once per change of the inputs, not on
	 * every call: they are asked for hundreds of times per frame.
	 */
	private Layout layout() {
		FarmLoader.Entry farm = farm();
		FarmDetailsPayload details = LoadedFarmsView.details();
		FarmAlertsPayload alertPage = LoadedFarmsView.alerts();
		String summary = farm == null ? "" : summaryText(farm).getString();
		int width = ui.view().flowWidth();
		Layout current = layout;
		if (current == null || current.width != width || current.farm != farm || current.details != details || current.alertPage != alertPage || !current.summary.equals(summary)) {
			current = new Layout(farm, details, alertPage, summary, width);
			layout = current;
		}
		return current;
	}

	/** The page's own snapshot of the text offsets for one set of inputs, compared by identity. */
	private final class Layout {
		private final FarmLoader.Entry farm;
		private final FarmDetailsPayload details;
		private final FarmAlertsPayload alertPage;
		private final String summary;
		private final int width;
		private final int status;
		private final int storage;
		private final int items;
		private final int blocks;
		private final int condition;
		private final int needs;
		private final int alerts;
		private final int rows;

		private Layout(FarmLoader.Entry farm, FarmDetailsPayload details, FarmAlertsPayload alertPage, String summary, int width) {
			this.farm = farm;
			this.details = details;
			this.alertPage = alertPage;
			this.summary = summary;
			this.width = width;
			status = CARD_HEIGHT + 8;
			storage = status + (farm == null ? 1 : lines(summaryText(farm))) * LINE_HEIGHT + 3;
			items = storage + lines(storageText(details)) * LINE_HEIGHT + 2;
			blocks = items + itemHeight(details == null ? List.of() : details.items());
			condition = blocks + (details == null ? 1 : lines(infoText(details))) * LINE_HEIGHT + 2;
			needs = condition + (details == null ? 1 : lines(condition(details))) * LINE_HEIGHT + 2;
			alerts = needs + Math.max(NEEDS_HEIGHT, itemHeight(details == null ? List.of() : details.needs()));
			rows = alerts + alertsHeight(farm, alertPage) + 4;
		}

		private int alertsHeight(FarmLoader.Entry farm, FarmAlertsPayload alertPage) {
			if (farm == null || farm.alerts() <= 0) {
				return 0;
			}
			// At least a line per alert the list announced, so the buttons don't move down when the alert page arrives.
			int height = 0;
			for (Text alert : AlertText.lines(farm, alertPage)) {
				height += lines(alert) * LINE_HEIGHT;
			}
			return Math.max(height, farm.alerts() * LINE_HEIGHT) + 2;
		}
	}

	/**
	 * The row of Take Down, which Forget takes for a farm the mod no longer has, since it has no Repair row above it to
	 * leave empty. Rows run without gaps: favorite, track, keep loaded, repair, take down, then the confirm row (only
	 * while confirming, its room kept so Back doesn't jump) and Back.
	 */
	static int takeDownRow(FarmLoader.Entry farm) {
		return farm.available() ? TAKE_DOWN : REPAIR;
	}

	static int confirmRow() {
		return TAKE_DOWN + 1;
	}

	/** Back follows Take Down, after the confirm row's room when the farm can be taken down, or right under Forget. */
	static int backRow(FarmLoader.Entry farm) {
		return farm != null && !farm.available() ? REPAIR + 1 : TAKE_DOWN + 2;
	}

}
