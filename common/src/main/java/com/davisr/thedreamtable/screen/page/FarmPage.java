package com.davisr.thedreamtable.screen.page;

import com.davisr.thedreamtable.client.LoadedFarmsView;
import com.davisr.thedreamtable.farm.FarmListing;
import com.davisr.thedreamtable.farm.FarmLoader;
import com.davisr.thedreamtable.farm.FarmRole;
import com.davisr.thedreamtable.farm.FarmTemplates;
import com.davisr.thedreamtable.network.FarmCarePayload;
import com.davisr.thedreamtable.network.FarmDetailsPayload;
import com.davisr.thedreamtable.network.FarmPreferencePayload;
import com.davisr.thedreamtable.screen.DreamTableScreenHandler;
import com.davisr.thedreamtable.screen.ui.SearchBox;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
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
	private static final int ALERTS = 0;
	private static final int FAVORITE = 1;
	private static final int TRACK = 2;
	private static final int LOADED = 3;
	private static final int REPAIR = 4;
	private static final int RENAME = 5;
	private static final int INVITE_VIEWER = 6;
	private static final int INVITE_OPERATOR = 7;
	private static final int COLLABORATORS = 8;
	private static final long CONFIRM_MS = 3000L;
	private static final int ITEM_SPACING = 21;
	private static final int ITEM_SIZE = 16;
	private static final int ITEM_ROW_HEIGHT = 28;
	private static final int LINE_HEIGHT = 10;
	/** Room under the condition line for the items a repair takes, or the four lines of the Take Down warning. */
	private static final int NEEDS_HEIGHT = 40;

	private final PageContext ui;
	private final SearchBox input;
	private long confirmUntil;

	public FarmPage(PageContext ui, SearchBox input) {
		this.ui = ui;
		this.input = input;
	}

	private FarmLoader.Entry farm() {
		int index = LoadedFarmsView.selectedIndex();
		return index < 0 ? null : LoadedFarmsView.farms().get(index);
	}

	@Override
	public void draw(DrawContext context, int mouseX, int mouseY) {
		LoadedFarmsView.ensure();
		LoadedFarmsView.ensureDetails();
		FarmLoader.Entry farm = farm();
		if (farm == null) {
			ui.flowNote(context, Text.translatable(LoadedFarmsView.received() ? "gui.the_dream_table.farm_gone" : "gui.the_dream_table.loaded_waiting"),
					ui.view().cardY(0) + 8, HINT);
			ui.buttonAt("back", context, Text.translatable("gui.the_dream_table.back"), flowX(), rowY(backRow()), mouseX, mouseY, true, false);
			return;
		}
		Text where = Text.translatable("gui.the_dream_table.farm_at", farm.where().getX(), farm.where().getY(), farm.where().getZ(),
				LoadedFarmsPage.dimension(farm.dimension()));
		LoadedFarmsPage.card(ui, context, farm, where, flowX(), ui.view().cardY(0), mouseX, mouseY);
		drawWrapped(context, summaryText(farm), statusY(), Integer.MAX_VALUE, HINT);
		drawStorage(context);
		drawCondition(context, farm);
		ui.buttonAt("alerts", context, Text.translatable("gui.the_dream_table.alerts"), flowX(), rowY(ALERTS), mouseX, mouseY, true, false);
		ui.buttonAt("favorite", context, Text.translatable(farm.favorite() ? "gui.the_dream_table.unfavorite" : "gui.the_dream_table.favorite"),
				flowX(), rowY(FAVORITE), mouseX, mouseY, true, farm.favorite());
		ui.buttonAt("track", context, Text.translatable(farm.tracked() ? "gui.the_dream_table.untrack" : "gui.the_dream_table.track"),
				flowX(), rowY(TRACK), mouseX, mouseY, true, farm.tracked());
		boolean operates = farm.role() != FarmRole.VIEWER;
		ui.buttonAt("keep_loaded", context, loadedLabel(farm), flowX(), rowY(LOADED), mouseX, mouseY, operates && canLoad(farm), farm.loaded());
		if (farm.available()) {
			boolean busy = ProjectState.locked(ui.handler());
			ui.buttonAt("repair", context, busy ? Text.translatable("gui.the_dream_table.repair_table_busy") : repairLabel(farm), flowX(), rowY(REPAIR), mouseX, mouseY,
					operates && farm.missing() != 0 && !busy, false);
		}
		if (farm.role() == FarmRole.OWNER) {
			ui.buttonAt("rename", context, Text.translatable("gui.the_dream_table.rename_from_field"), flowX(), rowY(RENAME), mouseX, mouseY, true, false);
			ui.buttonAt("invite_viewer", context, Text.translatable("gui.the_dream_table.invite_viewer"), flowX(), rowY(INVITE_VIEWER), mouseX, mouseY,
					!input.text().isBlank(), false);
			ui.buttonAt("invite_operator", context, Text.translatable("gui.the_dream_table.invite_operator"), flowX(), rowY(INVITE_OPERATOR), mouseX, mouseY,
					!input.text().isBlank(), false);
			List<FarmDetailsPayload.CollaboratorEntry> collaborators = collaborators();
			for (int index = 0; index < collaborators.size(); index++) {
				FarmDetailsPayload.CollaboratorEntry collaborator = collaborators.get(index);
				Text role = Text.translatable("gui.the_dream_table.role." + collaborator.role().name().toLowerCase(Locale.ROOT));
				ui.destructiveButtonAt("remove." + collaborator.playerUuid(), context,
						Text.translatable("gui.the_dream_table.remove_collaborator", collaborator.playerName(), role),
						flowX(), rowY(COLLABORATORS + index), mouseX, mouseY, true);
			}
			if (!farm.available()) {
				ui.destructiveButtonAt("forget", context, Text.translatable("gui.the_dream_table.forget"), flowX(), rowY(takeDownRow()), mouseX, mouseY, true);
			} else if (confirming()) {
				ui.buttonAt("cancel_take_down", context, Text.translatable("gui.the_dream_table.cancel"), flowX(), rowY(takeDownRow()), mouseX, mouseY, true, false);
				ui.destructiveButtonAt("confirm_take_down", context, Text.translatable("gui.the_dream_table.confirm_take_down"), flowX(), rowY(confirmRow()), mouseX, mouseY, true);
			} else {
				ui.destructiveButtonAt("take_down", context, Text.translatable("gui.the_dream_table.take_down"), flowX(), rowY(takeDownRow()), mouseX, mouseY, true);
			}
		}
		ui.buttonAt("back", context, Text.translatable("gui.the_dream_table.back"), flowX(), rowY(backRow()), mouseX, mouseY, true, false);
	}

	@Override
	public boolean click(double mouseX, double mouseY) {
		FarmLoader.Entry farm = farm();
		if (ui.buttonHit(mouseX, mouseY, flowX(), rowY(backRow()))) {
			confirmUntil = 0L;
			ui.go(DreamTableScreenHandler.PAGE_LOADED, DreamTableScreenHandler.BUTTON_FARM_BACK);
			return true;
		}
		if (farm == null) {
			return false;
		}
		if (ui.buttonHit(mouseX, mouseY, flowX(), rowY(ALERTS))) {
			ui.go(DreamTableScreenHandler.PAGE_ALERTS, DreamTableScreenHandler.BUTTON_ALERTS);
			return true;
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
			if (farm.role() == FarmRole.VIEWER) {
				return true;
			}
			if (!canLoad(farm)) {
				ui.actions().message(Text.translatable("gui.the_dream_table.loaded_full", LoadedFarmsView.limit()));
				return true;
			}
			LoadedFarmsView.toggle(farm.key());
			ui.actions().click();
			return true;
		}
		if (farm.available() && farm.role() != FarmRole.VIEWER && ui.buttonHit(mouseX, mouseY, flowX(), rowY(REPAIR)) && farm.missing() != 0) {
			if (ProjectState.locked(ui.handler())) {
				ui.message("gui.the_dream_table.repair_busy");
				return true;
			}
			ui.handler().preview(farm.designId(), DreamTableScreenHandler.PAGE_WORK);
			LoadedFarmsView.care(ui.handler().getTablePos(), farm.key(), FarmCarePayload.Action.REPAIR);
			ui.actions().click();
			return true;
		}
		if (farm.role() == FarmRole.OWNER && ui.buttonHit(mouseX, mouseY, flowX(), rowY(RENAME))) {
			LoadedFarmsView.rename(farm.key(), input.text());
			return true;
		}
		if (farm.role() == FarmRole.OWNER && ui.buttonHit(mouseX, mouseY, flowX(), rowY(INVITE_VIEWER)) && !input.text().isBlank()) {
			LoadedFarmsView.invite(farm.key(), input.text(), FarmRole.VIEWER);
			return true;
		}
		if (farm.role() == FarmRole.OWNER && ui.buttonHit(mouseX, mouseY, flowX(), rowY(INVITE_OPERATOR)) && !input.text().isBlank()) {
			LoadedFarmsView.invite(farm.key(), input.text(), FarmRole.OPERATOR);
			return true;
		}
		if (farm.role() == FarmRole.OWNER) {
			List<FarmDetailsPayload.CollaboratorEntry> collaborators = collaborators();
			for (int index = 0; index < collaborators.size(); index++) {
				if (ui.buttonHit(mouseX, mouseY, flowX(), rowY(COLLABORATORS + index))) {
					LoadedFarmsView.removeCollaborator(farm.key(), collaborators.get(index).playerUuid());
					ui.actions().click();
					return true;
				}
			}
		}
		if (!farm.available()) {
			if (farm.role() == FarmRole.OWNER && ui.buttonHit(mouseX, mouseY, flowX(), rowY(takeDownRow()))) {
				LoadedFarmsView.care(ui.handler().getTablePos(), farm.key(), FarmCarePayload.Action.FORGET);
				ui.actions().click();
				ui.go(DreamTableScreenHandler.PAGE_LOADED, DreamTableScreenHandler.BUTTON_FARM_BACK);
				return true;
			}
			return false;
		}
		if (farm.role() == FarmRole.OWNER && ui.buttonHit(mouseX, mouseY, flowX(), rowY(takeDownRow()))) {
			if (confirming()) {
				confirmUntil = 0L;
				ui.actions().click();
				return true;
			} else {
				confirmUntil = Util.getMeasuringTimeMs() + CONFIRM_MS;
				ui.actions().click();
				ui.actions().message(Text.translatable("gui.the_dream_table.take_down_click", LoadedFarmsPage.name(farm)));
			}
			return true;
		}
		if (farm.role() == FarmRole.OWNER && confirming() && ui.buttonHit(mouseX, mouseY, flowX(), rowY(confirmRow()))) {
			confirmUntil = 0L;
			LoadedFarmsView.care(ui.handler().getTablePos(), farm.key(), FarmCarePayload.Action.TAKE_DOWN);
			ui.actions().click();
			ui.go(DreamTableScreenHandler.PAGE_LOADED, DreamTableScreenHandler.BUTTON_FARM_BACK);
			return true;
		}
		return false;
	}

	/** Storage use, the items held with their counts, and the farm's block count, from the latest {@link FarmDetailsPayload}. */
	private void drawStorage(DrawContext context) {
		FarmDetailsPayload details = LoadedFarmsView.details();
		drawWrapped(context, storageText(details), storageY(), Integer.MAX_VALUE, NAME);
		if (details != null) {
			drawItems(context, details.items(), details.counts(), itemsY());
			drawWrapped(context, infoText(details), blocksY(), Integer.MAX_VALUE, HINT);
		}
	}

	/** Role, operating state, Keep Loaded state, and distance or dimension for the selected farm. */
	private static Text summaryText(FarmLoader.Entry farm) {
		return summaryText(farm, LoadedFarmsView.viewer());
	}

	static Text summaryText(FarmLoader.Entry farm, FarmListing.Viewer viewer) {
		Text role = Text.translatable("gui.the_dream_table.role." + farm.role().name().toLowerCase(Locale.ROOT));
		Text state = farm.available() ? LoadedFarmsPage.state(farm) : Text.translatable(FarmTemplates.isImported(farm.farmId())
				? "gui.the_dream_table.farm_file_missing" : "gui.the_dream_table.farm_removed");
		Text loadedState = Text.translatable(farm.loaded() ? "gui.the_dream_table.kept_loaded" : "gui.the_dream_table.not_kept_loaded");
		int away = FarmListing.blocksAway(farm, viewer);
		Text location = away >= 0 ? Text.translatable("gui.the_dream_table.blocks_away", away) : LoadedFarmsPage.dimension(farm.dimension());
		Text operating = Text.translatable("gui.the_dream_table.joined", loadedState, location);
		return Text.translatable("gui.the_dream_table.joined", role,
				Text.translatable("gui.the_dream_table.joined", state, operating));
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
		return text == null ? 1 : Math.max(1, ui.painter().text().wrapLines(text, CARD_WIDTH).size());
	}

	/**
	 * How much of the farm stands: blocks missing, which Repair places, and blocks the player changed, which it leaves.
	 * Below, the items the missing blocks take, or, while Take Down waits for its second click, what it will do.
	 */
	private void drawCondition(DrawContext context, FarmLoader.Entry farm) {
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
		drawItems(context, details.needs(), details.needCounts(), needsY());
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
		for (OrderedText line : ui.painter().text().wrapLines(text, CARD_WIDTH)) {
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

	private void drawItems(DrawContext context, List<String> items, List<Integer> counts, int y) {
		int x = flowX();
		for (int index = 0; index < items.size(); index++) {
			Identifier id = Identifier.tryParse(items.get(index));
			if (id == null || !Registries.ITEM.containsId(id)) {
				continue;
			}
			int itemX = x + itemXOffset(index);
			int itemY = y + itemRow(index) * ITEM_ROW_HEIGHT;
			context.drawItem(new ItemStack(Registries.ITEM.get(id)), itemX, itemY);
			context.drawText(ui.painter().text(), shortCount(counts.get(index)), itemX, itemY + 17, HINT, false);
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

	private static Text loadedLabel(FarmLoader.Entry farm) {
		if (farm.loaded()) {
			return Text.translatable("gui.the_dream_table.keep_loaded_on");
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
		return rowY(backRow()) - ui.view().cardY(0) + PAD + BUTTON_HEIGHT + 4;
	}

	@Override
	public Text subtitle() {
		return Text.translatable("gui.the_dream_table.my_farms");
	}

	private int statusY() {
		return ui.view().cardY(0) + CARD_HEIGHT + 8;
	}

	private int flowX() {
		return ui.view().flowX();
	}

	/** Each text block below the card wraps to as many lines as it needs, and what follows moves down with it. */
	private int storageY() {
		FarmLoader.Entry farm = farm();
		return statusY() + (farm == null ? 1 : lines(summaryText(farm))) * LINE_HEIGHT + 3;
	}

	private int itemsY() {
		return storageY() + lines(storageText(LoadedFarmsView.details())) * LINE_HEIGHT + 2;
	}

	private int blocksY() {
		FarmDetailsPayload details = LoadedFarmsView.details();
		return itemsY() + itemHeight(details == null ? List.of() : details.items());
	}

	private int conditionY() {
		FarmDetailsPayload details = LoadedFarmsView.details();
		return blocksY() + (details == null ? 1 : lines(infoText(details))) * LINE_HEIGHT + 2;
	}

	private int needsY() {
		FarmDetailsPayload details = LoadedFarmsView.details();
		return conditionY() + (details == null ? 1 : lines(condition(details))) * LINE_HEIGHT + 2;
	}

	private int rowY(int row) {
		return needsY() + needsHeight() + 4 + row * (BUTTON_HEIGHT + BUTTON_GAP);
	}

	private int needsHeight() {
		FarmDetailsPayload details = LoadedFarmsView.details();
		return Math.max(NEEDS_HEIGHT, itemHeight(details == null ? List.of() : details.needs()));
	}

	private List<FarmDetailsPayload.CollaboratorEntry> collaborators() {
		FarmDetailsPayload details = LoadedFarmsView.details();
		return details == null ? List.of() : details.collaborators();
	}

	private int takeDownRow() {
		return COLLABORATORS + collaborators().size();
	}

	private int confirmRow() {
		return takeDownRow() + 1;
	}

	private int backRow() {
		return takeDownRow() + 2;
	}

}
