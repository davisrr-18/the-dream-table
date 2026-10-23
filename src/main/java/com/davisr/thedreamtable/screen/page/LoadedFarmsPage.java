package com.davisr.thedreamtable.screen.page;

import com.davisr.thedreamtable.client.LoadedFarmsView;
import com.davisr.thedreamtable.farm.FarmLoader;
import com.davisr.thedreamtable.farm.FarmOptions;
import com.davisr.thedreamtable.farm.FarmOptions.FarmOption;
import com.davisr.thedreamtable.farm.FarmSize;
import com.davisr.thedreamtable.farm.FarmTemplate;
import com.davisr.thedreamtable.farm.FarmTemplates;
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
 * Every farm this player finished, in every dimension, newest first. Clicking one keeps it loaded, or stops keeping it,
 * up to the server's limit. The list comes from the server each time the page opens.
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

	private void card(DrawContext context, FarmLoader.Entry farm, int y, int mouseX, int mouseY) {
		FarmOption option = FarmOptions.byId(farm.farmId()).orElse(null);
		ItemStack icon = option == null ? new ItemStack(Items.STRUCTURE_BLOCK) : option.icon().getDefaultStack();
		Text name = option == null ? Text.literal(farm.farmId()) : option.title();
		Text where = Text.translatable("gui.the_dream_table.farm_at", farm.where().getX(), farm.where().getY(), farm.where().getZ(), dimension(farm.dimension()));
		FarmTemplate template = FarmTemplates.get(farm.farmId());
		if (template == null) {
			ui.card(context, icon, name, where, y, mouseX, mouseY, farm.loaded(), true);
			return;
		}
		FarmSize size = FarmSize.of(template.steps().size());
		ui.card(context, icon, name, where, size.title(), FarmCard.color(size), y, mouseX, mouseY, farm.loaded(), true);
	}

	private static Text dimension(String id) {
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
				LoadedFarmsView.toggle(farms.get(index).key());
				ui.actions().click();
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
		return Text.translatable("gui.the_dream_table.loaded_title", LoadedFarmsView.loaded(), LoadedFarmsView.limit());
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
