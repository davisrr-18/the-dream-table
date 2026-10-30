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
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

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
	private static final int CHIPS = 4;
	private static final int SHOW = 0;
	private static final int SORT = 1;
	private static final int DIMENSION = 2;
	private static final int FAVORITES = 3;
	private static final int COUNT_HEIGHT = 12;

	private final PageContext ui;
	private final SearchBox search;
	/** The last filtered and sorted result with the inputs it was made from, so a frame filters and sorts once. */
	private List<Integer> shownCache = List.of();
	private List<FarmLoader.Entry> shownFarms;
	private String shownQuery;
	private FarmListing.Show shownShow;
	private FarmListing.Sort shownSort;
	private boolean shownFavorites;
	private String shownDimension;
	private FarmListing.Viewer shownViewer;

	public LoadedFarmsPage(PageContext ui, SearchBox search) {
		this.ui = ui;
		this.search = search;
	}

	private boolean listing() {
		return lists(LoadedFarmsView.received(), LoadedFarmsView.farms().size());
	}

	/** The list shows whenever there are farms: a server limit of 0 only turns Keep Loaded off, it never hides them. */
	static boolean lists(boolean received, int farms) {
		return received && farms > 0;
	}

	private List<Integer> shown() {
		if (!listing()) {
			return List.of();
		}
		List<FarmLoader.Entry> farms = LoadedFarmsView.farms();
		String query = search.text();
		FarmListing.Viewer viewer = LoadedFarmsView.viewer();
		if (farms != shownFarms || !query.equals(shownQuery) || LoadedFarmsView.show() != shownShow || LoadedFarmsView.sort() != shownSort
				|| LoadedFarmsView.favoritesOnly() != shownFavorites || !LoadedFarmsView.dimension().equals(shownDimension)
				|| !Objects.equals(viewer, shownViewer)) {
			shownCache = LoadedFarmsView.shown(query, farm -> name(farm).getString());
			shownFarms = farms;
			shownQuery = query;
			shownShow = LoadedFarmsView.show();
			shownSort = LoadedFarmsView.sort();
			shownFavorites = LoadedFarmsView.favoritesOnly();
			// Read after the call: it clears a dimension the player has no farm in anymore.
			shownDimension = LoadedFarmsView.dimension();
			shownViewer = viewer;
		}
		return shownCache;
	}

	@Override
	public void draw(DrawContext context, int mouseX, int mouseY) {
		LoadedFarmsView.ensure();
		List<FarmLoader.Entry> farms = LoadedFarmsView.farms();
		if (!listing()) {
			String key = !LoadedFarmsView.received() ? "gui.the_dream_table.loaded_waiting" : "gui.the_dream_table.loaded_empty";
			ui.collectionNote(context, Text.translatable(key), chipY(0), HINT);
		} else {
			List<Integer> shown = shown();
			drawControls(context, mouseX, mouseY, shown.size(), farms.size());
			if (shown.isEmpty()) {
				ui.collectionNote(context, Text.translatable("gui.the_dream_table.farms_none_match"), cardY(0), HINT);
			}
			FarmListing.Viewer viewer = LoadedFarmsView.viewer();
			for (int place = 0; place < shown.size(); place++) {
				FarmLoader.Entry farm = farms.get(shown.get(place));
				card(ui, context, farm, detail(farm, viewer), cardX(place), cardY(place), mouseX, mouseY);
			}
		}
		boolean keepingOff = LoadedFarmsView.received() && LoadedFarmsView.limit() <= 0;
		ui.collectionNote(context, Text.translatable(keepingOff ? "gui.the_dream_table.loaded_off" : "gui.the_dream_table.loaded_hint"), noteY(), HINT);
	}

	private void drawControls(DrawContext context, int mouseX, int mouseY, int shown, int total) {
		String showKey = "gui.the_dream_table.show." + LoadedFarmsView.show().name().toLowerCase(Locale.ROOT);
		String sortKey = "gui.the_dream_table.sort." + LoadedFarmsView.sort().name().toLowerCase(Locale.ROOT);
		Text where = LoadedFarmsView.dimension().isEmpty() ? Text.translatable("gui.the_dream_table.dimension_all") : dimension(LoadedFarmsView.dimension());
		chip(context, Text.translatable("gui.the_dream_table.show", Text.translatable(showKey)), SHOW, mouseX, mouseY);
		chip(context, Text.translatable("gui.the_dream_table.sort", Text.translatable(sortKey)), SORT, mouseX, mouseY);
		chip(context, Text.translatable("gui.the_dream_table.dimension_filter", where), DIMENSION, mouseX, mouseY);
		String favoritesKey = LoadedFarmsView.favoritesOnly() ? "gui.the_dream_table.favorites_only" : "gui.the_dream_table.favorites_all";
		chip(context, Text.translatable("gui.the_dream_table.favorites_filter", Text.translatable(favoritesKey)), FAVORITES, mouseX, mouseY);
		Text count = shown == total ? Text.translatable(total == 1 ? "gui.the_dream_table.farm_count_one" : "gui.the_dream_table.farm_count", total)
				: Text.translatable("gui.the_dream_table.farms_shown", shown, total);
		ui.collectionNote(context, count, countY() + 1, HINT);
	}

	private void chip(DrawContext context, Text label, int row, int mouseX, int mouseY) {
		ui.chipAt("filter." + row, context, label, filterX(ui.view(), row), chipY(row), CARD_WIDTH, CHIP_HEIGHT, mouseX, mouseY);
	}

	private boolean chipHit(double mouseX, double mouseY, int row) {
		return ui.view().hit(mouseX, mouseY, filterX(ui.view(), row), chipY(row), CARD_WIDTH, CHIP_HEIGHT);
	}

	static void card(PageContext ui, DrawContext context, FarmLoader.Entry farm, Text detail, int y, int mouseX, int mouseY) {
		card(ui, context, farm, detail, ui.view().cardX(), y, mouseX, mouseY);
	}

	static void card(PageContext ui, DrawContext context, FarmLoader.Entry farm, Text detail, int x, int y, int mouseX, int mouseY) {
		card(ui, context, farm, detail, x, y, CARD_WIDTH, mouseX, mouseY);
	}

	static void card(PageContext ui, DrawContext context, FarmLoader.Entry farm, Text detail, int x, int y, int width, int mouseX, int mouseY) {
		FarmOption option = FarmOptions.byId(farm.farmId()).orElse(null);
		ItemStack icon = option == null ? new ItemStack(Items.STRUCTURE_BLOCK) : option.icon().getDefaultStack();
		ui.cardAt(farm.key(), context, icon, AlertText.listName(farm, name(farm)), detail, x, y, width, mouseX, mouseY, farm.loaded(), true);
	}

	/** The farm's name, its schematic's file name, or "Removed Farm" for one the mod no longer has. */
	public static Text name(FarmLoader.Entry farm) {
		return name(farm.customName(), farm.farmId());
	}

	public static Text name(String customName, String farmId) {
		if (!customName.isEmpty()) {
			return Text.literal(customName);
		}
		FarmOption option = FarmOptions.byId(farmId).orElse(null);
		return option != null ? option.title() : FarmTemplates.isImported(farmId)
				? Text.literal(farmId.substring(ImportedStructures.ID_PREFIX.length()))
				: Text.translatable("gui.the_dream_table.retired_name");
	}

	/** "Running · 45% full · 12 missing · 120 m": its state, then what needs a look, then how far it is or where. */
	private static Text detail(FarmLoader.Entry farm, FarmListing.Viewer viewer) {
		Text text = state(farm);
		if (farm.favorite()) {
			text = joined(Text.translatable("gui.the_dream_table.favorite_short"), text);
		}
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

	public static Text dimension(String id) {
		Identifier dimension = Identifier.tryParse(id);
		if (dimension != null && dimension.getNamespace().equals("minecraft")) {
			return Text.translatable("gui.the_dream_table.dimension." + dimension.getPath());
		}
		return Text.literal(dimension == null ? id : dimension.getPath());
	}

	@Override
	public boolean click(double mouseX, double mouseY) {
		if (listing()) {
			if (chipHit(mouseX, mouseY, SHOW) || chipHit(mouseX, mouseY, SORT) || chipHit(mouseX, mouseY, DIMENSION)
					|| chipHit(mouseX, mouseY, FAVORITES)) {
				if (chipHit(mouseX, mouseY, SHOW)) {
					LoadedFarmsView.nextShow();
				} else if (chipHit(mouseX, mouseY, SORT)) {
					LoadedFarmsView.nextSort();
				} else if (chipHit(mouseX, mouseY, DIMENSION)) {
					LoadedFarmsView.nextDimension();
				} else {
					LoadedFarmsView.toggleFavorites();
				}
				ui.view().resetScroll();
				ui.actions().click();
				return true;
			}
			List<Integer> shown = shown();
			for (int place = 0; place < shown.size(); place++) {
				if (ui.cardHit(mouseX, mouseY, cardX(place), cardY(place))) {
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

	private int cardY(int place) {
		return cardY(ui.view(), place);
	}

	private int cardX(int place) {
		return ui.view().gridCardX(place);
	}

	private int listHeight() {
		if (!listing()) {
			return NOTE_HEIGHT;
		}
		return listHeight(ui.view(), shown().size());
	}

	private int noteY() {
		return ui.view().cardY(0) + listHeight() + 6;
	}

	private int chipY(int row) {
		return filterY(ui.view(), row);
	}

	private int countY() {
		return ui.view().cardY(0) + filterRows(ui.view(), CHIPS) * (CHIP_HEIGHT + CHIP_GAP);
	}

	static int filterX(com.davisr.thedreamtable.screen.ui.Viewport view, int index) {
		return view.gridCardX(index);
	}

	static int filterY(com.davisr.thedreamtable.screen.ui.Viewport view, int index) {
		return view.cardY(0) + index / filterColumns(view) * (CHIP_HEIGHT + CHIP_GAP);
	}

	static int filterRows(com.davisr.thedreamtable.screen.ui.Viewport view, int count) {
		int columns = filterColumns(view);
		return (Math.max(0, count) + columns - 1) / columns;
	}

	static int controlsHeight(com.davisr.thedreamtable.screen.ui.Viewport view) {
		return filterRows(view, CHIPS) * (CHIP_HEIGHT + CHIP_GAP) + COUNT_HEIGHT + 2;
	}

	static int cardY(com.davisr.thedreamtable.screen.ui.Viewport view, int place) {
		return view.gridCardY(place) + controlsHeight(view);
	}

	static int listHeight(com.davisr.thedreamtable.screen.ui.Viewport view, int count) {
		return controlsHeight(view) + (count == 0 ? NOTE_HEIGHT : cardY(view, count - 1) - cardY(view, 0) + CARD_HEIGHT);
	}

	private static int filterColumns(com.davisr.thedreamtable.screen.ui.Viewport view) {
		return view.gridCardX(0) == view.gridCardX(1) ? 1 : 2;
	}

}
