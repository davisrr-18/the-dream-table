package com.davisr.thedreamtable.screen.page;

import com.davisr.thedreamtable.farm.FarmOptions;
import com.davisr.thedreamtable.farm.FarmOptions.Category;
import com.davisr.thedreamtable.farm.FarmOptions.FarmOption;
import com.davisr.thedreamtable.farm.BuildPace;
import com.davisr.thedreamtable.farm.FarmTemplates;
import com.davisr.thedreamtable.screen.DreamTableScreenHandler;
import com.davisr.thedreamtable.screen.ui.Viewport;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.List;

import static com.davisr.thedreamtable.screen.ui.Theme.*;

/** The farms of one category, each drawn as a {@link FarmCard} with its block count and how long it would take. */
public final class CategoryPage implements TablePage {
	private final PageContext ui;

	public CategoryPage(PageContext ui) {
		this.ui = ui;
	}

	public static boolean isCategoryPage(int page) {
		return Category.byIndex(page - DreamTableScreenHandler.PAGE_CATEGORY).isPresent();
	}

	private Category category() {
		return Category.byIndex(ui.handler().getPage() - DreamTableScreenHandler.PAGE_CATEGORY).orElse(Category.PLANTS);
	}

	@Override
	public void draw(DrawContext context, int mouseX, int mouseY) {
		List<FarmOption> farms = category().farms();
		boolean locked = ProjectState.locked(ui.handler());
		for (int index = 0; index < farms.size(); index++) {
			FarmOption option = farms.get(index);
			boolean selected = option.id().equals(ui.farm());
			drawCard(context, option, cardX(ui.view(), index), cardY(ui.view(), index), mouseX, mouseY, selected, !locked || selected);
		}
		ui.button(context, Text.translatable("gui.the_dream_table.back"), backY(), mouseX, mouseY, true, false);
	}

	@Override
	public boolean click(double mouseX, double mouseY) {
		List<FarmOption> farms = category().farms();
		for (int index = 0; index < farms.size(); index++) {
			if (ui.cardHit(mouseX, mouseY, cardX(ui.view(), index), cardY(ui.view(), index))) {
				FarmOption option = farms.get(index);
				if (!option.id().equals(ui.farm()) && ProjectState.locked(ui.handler())) {
					ui.message("gui.the_dream_table.farm_busy");
					return true;
				}
				ui.go(option.id(), DreamTableScreenHandler.PAGE_WORK, FarmOptions.ALL.indexOf(option));
				return true;
			}
		}
		if (ui.buttonHit(mouseX, mouseY, backY())) {
			ui.go(DreamTableScreenHandler.PAGE_SELECT, DreamTableScreenHandler.BUTTON_CATEGORY_BACK);
			return true;
		}
		return false;
	}

	@Override
	public int contentHeight() {
		int count = category().farms().size();
		return contentHeight(ui.view(), count);
	}

	@Override
	public Text subtitle() {
		return category().title();
	}

	private int backY() {
		return backY(ui.view(), category().farms().size());
	}

	static int cardX(Viewport view, int index) {
		return view.gridCardX(index);
	}

	static int cardY(Viewport view, int index) {
		return view.gridCardY(index);
	}

	static int backY(Viewport view, int count) {
		return cardY(view, Math.max(0, count - 1)) + CARD_HEIGHT + 6;
	}

	static int contentHeight(Viewport view, int count) {
		return view.gridContentHeight(count) + BUTTON_HEIGHT + 4;
	}

	private void drawCard(DrawContext context, FarmOption option, int x, int y, int mouseX, int mouseY, boolean selected, boolean enabled) {
		int steps = FarmTemplates.stepCount(option.id());
		if (steps < 0) {
			ui.cardAt(option.id(), context, option.icon().getDefaultStack(), option.title(), x, y, mouseX, mouseY, selected, enabled);
			return;
		}
		int seconds = BuildPace.secondsLeftAt(ui.handler().blocksPerSecondAt(ui.handler().getPace()), steps, 0);
		Text detail = Text.translatable("gui.the_dream_table.with_eta", Text.translatable("gui.the_dream_table.farm_size", steps), TimeText.of(seconds));
		int warnings = FarmTemplates.notes(option.id()).size();
		if (warnings > 0) {
			Text count = Text.translatable(warnings == 1 ? "gui.the_dream_table.warning_one" : "gui.the_dream_table.warnings", warnings);
			detail = Text.translatable("gui.the_dream_table.joined", detail, count);
		}
		ui.cardAt(option.id(), context, option.icon().getDefaultStack(), option.title(), detail, x, y, mouseX, mouseY, selected, enabled);
	}
}
