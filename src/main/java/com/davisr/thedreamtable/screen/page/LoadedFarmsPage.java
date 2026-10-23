package com.davisr.thedreamtable.screen.page;

import com.davisr.thedreamtable.client.LoadedFarmsView;
import com.davisr.thedreamtable.farm.FarmLoader;
import com.davisr.thedreamtable.farm.FarmOptions;
import com.davisr.thedreamtable.farm.FarmOptions.FarmOption;
import com.davisr.thedreamtable.farm.FarmStorage;
import com.davisr.thedreamtable.farm.FarmTemplates;
import com.davisr.thedreamtable.farm.ImportedStructures;
import com.davisr.thedreamtable.screen.DreamTableScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;

import static com.davisr.thedreamtable.screen.ui.Theme.*;

/**
 * My Farms: every farm this player finished, in every dimension, newest first, with whether it is running.
 * Clicking one opens {@link FarmPage}. The list comes from the server each time the page opens.
 */
public final class LoadedFarmsPage implements TablePage {
	private static final int NOTE_HEIGHT = 36;

	private final PageContext ui;

	public LoadedFarmsPage(PageContext ui) {
		this.ui = ui;
	}

	@Override
	public void draw(DrawContext context, int mouseX, int mouseY) {
		LoadedFarmsView.ensure();
		List<FarmLoader.Entry> farms = LoadedFarmsView.farms();
		if (!LoadedFarmsView.received() || LoadedFarmsView.limit() <= 0 || farms.isEmpty()) {
			String key = !LoadedFarmsView.received() ? "gui.the_dream_table.loaded_waiting"
					: LoadedFarmsView.limit() <= 0 ? "gui.the_dream_table.loaded_off" : "gui.the_dream_table.loaded_empty";
			note(context, Text.translatable(key), ui.view().cardY(0));
		} else {
			for (int index = 0; index < farms.size(); index++) {
				card(context, farms.get(index), ui.view().cardY(index), mouseX, mouseY);
			}
		}
		note(context, Text.translatable("gui.the_dream_table.loaded_hint"), noteY());
		ui.button(context, Text.translatable("gui.the_dream_table.back"), backY(), mouseX, mouseY, true, false);
	}

	static void card(PageContext ui, DrawContext context, FarmLoader.Entry farm, Text detail, int y, int mouseX, int mouseY) {
		FarmOption option = FarmOptions.byId(farm.farmId()).orElse(null);
		ItemStack icon = option == null ? new ItemStack(Items.STRUCTURE_BLOCK) : option.icon().getDefaultStack();
		Text name = option != null ? option.title() : FarmTemplates.isImported(farm.farmId())
				? Text.literal(farm.farmId().substring(ImportedStructures.ID_PREFIX.length()))
				: Text.translatable("gui.the_dream_table.retired_name");
		ui.card(context, icon, name, detail, y, mouseX, mouseY, farm.loaded(), true);
	}

	private void card(DrawContext context, FarmLoader.Entry farm, int y, int mouseX, int mouseY) {
		Text where = Text.translatable("gui.the_dream_table.farm_status_at", status(farm.status()), farm.where().getX(), farm.where().getY(), farm.where().getZ());
		card(ui, context, farm, where, y, mouseX, mouseY);
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
		List<FarmLoader.Entry> farms = LoadedFarmsView.received() && LoadedFarmsView.limit() > 0 ? LoadedFarmsView.farms() : List.of();
		for (int index = 0; index < farms.size(); index++) {
			if (ui.cardHit(mouseX, mouseY, ui.view().cardY(index))) {
				LoadedFarmsView.select(farms.get(index).key());
				ui.go(DreamTableScreenHandler.PAGE_FARM, DreamTableScreenHandler.BUTTON_FARM_OPEN);
				return true;
			}
		}
		if (ui.buttonHit(mouseX, mouseY, backY())) {
			ui.go(DreamTableScreenHandler.PAGE_OPTIONS, DreamTableScreenHandler.BUTTON_LOADED_BACK);
			return true;
		}
		return false;
	}

	@Override
	public int contentHeight() {
		return PAD + listHeight() + 6 + NOTE_HEIGHT + BUTTON_HEIGHT + 4;
	}

	@Override
	public Text subtitle() {
		return Text.translatable("gui.the_dream_table.my_farms_title", LoadedFarmsView.loaded(), LoadedFarmsView.limit());
	}

	private int shownCards() {
		return LoadedFarmsView.received() && LoadedFarmsView.limit() > 0 ? LoadedFarmsView.farms().size() : 0;
	}

	private int listHeight() {
		int count = shownCards();
		return count == 0 ? NOTE_HEIGHT : count * CARD_HEIGHT + (count - 1) * CARD_GAP;
	}

	private int noteY() {
		return ui.view().cardY(0) + listHeight() + 6;
	}

	private int backY() {
		return noteY() + NOTE_HEIGHT;
	}
}
