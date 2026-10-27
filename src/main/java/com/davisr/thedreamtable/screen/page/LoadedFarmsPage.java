package com.davisr.thedreamtable.screen.page;

import com.davisr.thedreamtable.client.LoadedFarmsView;
import com.davisr.thedreamtable.farm.FarmListing;
import com.davisr.thedreamtable.farm.FarmLoader;
import com.davisr.thedreamtable.farm.FarmOptions;
import com.davisr.thedreamtable.farm.FarmOptions.FarmOption;
import com.davisr.thedreamtable.farm.FarmStorage;
import com.davisr.thedreamtable.farm.FarmTemplates;
import com.davisr.thedreamtable.farm.ImportedStructures;
import com.davisr.thedreamtable.screen.DreamTableScreenHandler;
import com.davisr.thedreamtable.screen.ui.SearchBox;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Locale;

import static com.davisr.thedreamtable.screen.ui.Theme.*;

/**
 * My Farms: every farm this player finished, in every dimension, found by name with the search box, filtered by state
 * and dimension, and sorted. Each card says whether the farm runs, how full its storage is, what is missing, and how far
 * it is; highlighted cards are kept loaded. Clicking one opens {@link FarmPage}. The list comes from the server each time
 * the page opens.
 */
public final class LoadedFarmsPage implements TablePage {
	private static final int NOTE_HEIGHT = 36;
	private static final int CHIP_HEIGHT = 14;
	private static final int CHIP_GAP = 3;
	private static final int CHIPS = 3;
	private static final int SHOW = 0;
	private static final int SORT = 1;
	private static final int DIMENSION = 2;
	private static final int COUNT_HEIGHT = 12;
	/** The filters and the count above the cards. */
	private static final int CONTROLS = CHIPS * (CHIP_HEIGHT + CHIP_GAP) + COUNT_HEIGHT + 2;

	private final PageContext ui;
	private final SearchBox search;

	public LoadedFarmsPage(PageContext ui, SearchBox search) {
		this.ui = ui;
		this.search = search;
	}

	private boolean listing() {
		return LoadedFarmsView.received() && LoadedFarmsView.limit() > 0 && !LoadedFarmsView.farms().isEmpty();
	}

	private List<Integer> shown() {
		return listing() ? LoadedFarmsView.shown(search.text(), farm -> name(farm).getString()) : List.of();
	}

	@Override
	public void draw(DrawContext context, int mouseX, int mouseY) {
		LoadedFarmsView.ensure();
		List<FarmLoader.Entry> farms = LoadedFarmsView.farms();
		if (!listing()) {
			String key = !LoadedFarmsView.received() ? "gui.the_dream_table.loaded_waiting"
					: LoadedFarmsView.limit() <= 0 ? "gui.the_dream_table.loaded_off" : "gui.the_dream_table.loaded_empty";
			note(context, Text.translatable(key), ui.view().cardY(0));
		} else {
			List<Integer> shown = shown();
			drawControls(context, mouseX, mouseY, shown.size(), farms.size());
			if (shown.isEmpty()) {
				note(context, Text.translatable("gui.the_dream_table.farms_none_match"), cardY(0));
			}
			FarmListing.Viewer viewer = LoadedFarmsView.viewer();
			for (int place = 0; place < shown.size(); place++) {
				FarmLoader.Entry farm = farms.get(shown.get(place));
				card(ui, context, farm, detail(farm, viewer), cardY(place), mouseX, mouseY);
			}
		}
		note(context, Text.translatable("gui.the_dream_table.loaded_hint"), noteY());
	}

	private void drawControls(DrawContext context, int mouseX, int mouseY, int shown, int total) {
		String showKey = "gui.the_dream_table.show." + LoadedFarmsView.show().name().toLowerCase(Locale.ROOT);
		String sortKey = "gui.the_dream_table.sort." + LoadedFarmsView.sort().name().toLowerCase(Locale.ROOT);
		Text where = LoadedFarmsView.dimension().isEmpty() ? Text.translatable("gui.the_dream_table.dimension_all") : dimension(LoadedFarmsView.dimension());
		chip(context, Text.translatable("gui.the_dream_table.show", Text.translatable(showKey)), SHOW, mouseX, mouseY);
		chip(context, Text.translatable("gui.the_dream_table.sort", Text.translatable(sortKey)), SORT, mouseX, mouseY);
		chip(context, Text.translatable("gui.the_dream_table.dimension_filter", where), DIMENSION, mouseX, mouseY);
		Text count = shown == total ? Text.translatable(total == 1 ? "gui.the_dream_table.farm_count_one" : "gui.the_dream_table.farm_count", total)
				: Text.translatable("gui.the_dream_table.farms_shown", shown, total);
		context.drawText(ui.painter().text(), count, ui.view().cardX(), chipY(CHIPS) + 1, HINT, false);
	}

	private void chip(DrawContext context, Text label, int row, int mouseX, int mouseY) {
		int y = chipY(row);
		ui.chip("filter." + row, context, label, y, CARD_WIDTH, CHIP_HEIGHT, mouseX, mouseY);
	}

	private boolean chipHit(double mouseX, double mouseY, int row) {
		return ui.view().hit(mouseX, mouseY, ui.view().cardX(), chipY(row), CARD_WIDTH, CHIP_HEIGHT);
	}

	static void card(PageContext ui, DrawContext context, FarmLoader.Entry farm, Text detail, int y, int mouseX, int mouseY) {
		FarmOption option = FarmOptions.byId(farm.farmId()).orElse(null);
		ItemStack icon = option == null ? new ItemStack(Items.STRUCTURE_BLOCK) : option.icon().getDefaultStack();
		ui.card(farm.key(), context, icon, name(farm), detail, y, mouseX, mouseY, farm.loaded(), true);
	}

	/** The farm's name, its schematic's file name, or "Removed Farm" for one the mod no longer has. */
	static Text name(FarmLoader.Entry farm) {
		FarmOption option = FarmOptions.byId(farm.farmId()).orElse(null);
		return option != null ? option.title() : FarmTemplates.isImported(farm.farmId())
				? Text.literal(farm.farmId().substring(ImportedStructures.ID_PREFIX.length()))
				: Text.translatable("gui.the_dream_table.retired_name");
	}

	/** "Running · 45% full · 12 missing · 120 m": its state, then what needs a look, then how far it is or where. */
	private static Text detail(FarmLoader.Entry farm, FarmListing.Viewer viewer) {
		Text text = state(farm);
		if (farm.missing() > 0) {
			text = joined(text, Text.translatable("gui.the_dream_table.missing_short", farm.missing()));
		}
		int away = FarmListing.blocksAway(farm, viewer);
		return joined(text, away >= 0 ? Text.translatable("gui.the_dream_table.blocks_away", away) : dimension(farm.dimension()));
	}

	/** Running with how full its storage is, storage full, not loaded, or why it can no longer be repaired. */
	static Text state(FarmLoader.Entry farm) {
		if (!farm.available()) {
			return Text.translatable("gui.the_dream_table.status_removed");
		}
		if (farm.status() == FarmStorage.RUNNING && farm.storage() >= 0) {
			return Text.translatable("gui.the_dream_table.status_running_storage", farm.storage());
		}
		return status(farm.status());
	}

	private static Text joined(Text first, Text second) {
		return Text.translatable("gui.the_dream_table.joined", first, second);
	}

	static Text status(int status) {
		return Text.translatable(switch (status) {
			case FarmStorage.RUNNING -> "gui.the_dream_table.status_running";
			case FarmStorage.FULL -> "gui.the_dream_table.status_full";
			default -> "gui.the_dream_table.status_unloaded";
		});
	}

	static Text dimension(String id) {
		Identifier dimension = Identifier.tryParse(id);
		if (dimension != null && dimension.getNamespace().equals("minecraft")) {
			return Text.translatable("gui.the_dream_table.dimension." + dimension.getPath());
		}
		return Text.literal(dimension == null ? id : dimension.getPath());
	}

	private void note(DrawContext context, Text text, int y) {
		int lineY = y;
		for (OrderedText line : ui.painter().text().wrapLines(text, CARD_WIDTH)) {
			context.drawText(ui.painter().text(), line, ui.view().cardX(), lineY, HINT, false);
			lineY += 11;
		}
	}

	@Override
	public boolean click(double mouseX, double mouseY) {
		if (listing()) {
			if (chipHit(mouseX, mouseY, SHOW) || chipHit(mouseX, mouseY, SORT) || chipHit(mouseX, mouseY, DIMENSION)) {
				if (chipHit(mouseX, mouseY, SHOW)) {
					LoadedFarmsView.nextShow();
				} else if (chipHit(mouseX, mouseY, SORT)) {
					LoadedFarmsView.nextSort();
				} else {
					LoadedFarmsView.nextDimension();
				}
				ui.view().resetScroll();
				ui.actions().click();
				return true;
			}
			List<Integer> shown = shown();
			for (int place = 0; place < shown.size(); place++) {
				if (ui.cardHit(mouseX, mouseY, cardY(place))) {
					LoadedFarmsView.select(LoadedFarmsView.farms().get(shown.get(place)).key());
					ui.go(DreamTableScreenHandler.PAGE_FARM, DreamTableScreenHandler.BUTTON_FARM_OPEN);
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public int contentHeight() {
		return PAD + listHeight() + 6 + NOTE_HEIGHT + 4;
	}

	@Override
	public Text subtitle() {
		return Text.translatable("gui.the_dream_table.my_farms_title", LoadedFarmsView.loaded(), LoadedFarmsView.limit());
	}

	private int chipY(int row) {
		return ui.view().cardY(0) + row * (CHIP_HEIGHT + CHIP_GAP);
	}

	private int cardY(int place) {
		return ui.view().cardY(place) + CONTROLS;
	}

	private int listHeight() {
		if (!listing()) {
			return NOTE_HEIGHT;
		}
		int count = shown().size();
		return CONTROLS + (count == 0 ? NOTE_HEIGHT : count * CARD_HEIGHT + (count - 1) * CARD_GAP);
	}

	private int noteY() {
		return ui.view().cardY(0) + listHeight() + 6;
	}

}
