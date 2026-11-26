package com.davisr.thedreamtable.screen.page;

import com.davisr.thedreamtable.farm.FarmOptions.Category;
import com.davisr.thedreamtable.screen.DreamTableScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.Locale;

import static com.davisr.thedreamtable.screen.ui.Theme.*;

/** The first menu page: the farm categories, then My Schematics and Options. The category of the current farm is highlighted. */
public final class SelectionPage implements TablePage {
	private static final Category[] CATEGORIES = Category.values();

	private final PageContext ui;

	public SelectionPage(PageContext ui) {
		this.ui = ui;
	}

	@Override
	public void draw(DrawContext context, int mouseX, int mouseY) {
		Category current = Category.of(ui.farm()).orElse(null);
		int queued = queuedCount();
		if (queued > 0) {
			// A queue nobody started waits for this button; Clear Queue drops it. Neither holds items, so neither can lose any.
			ui.segmentAt("queue_start", context, Text.translatable("gui.the_dream_table.queue_start", queued), ui.view().collectionX(), ui.view().cardY(0), SchemesPage.halfWidth(ui.view()), mouseX, mouseY, false, !ProjectState.locked(ui.handler()));
			ui.segmentAt("queue_clear", context, Text.translatable("gui.the_dream_table.queue_clear"), SchemesPage.folderX(ui.view()), ui.view().cardY(0), SchemesPage.halfWidth(ui.view()), mouseX, mouseY, false, true);
		}
		for (int index = 0; index < CATEGORIES.length; index++) {
			Category category = CATEGORIES[index];
			ui.cardAt("category." + category.name().toLowerCase(Locale.ROOT), context, category.icon().getDefaultStack(), category.title(), count(category),
					ui.view().gridCardX(index), cardY(index), mouseX, mouseY, category == current, true);
		}
	}

	private int queuedCount() {
		return ui.handler().getQueuedFarms().size();
	}

	/** The cards sit under the queue buttons while a queue exists. */
	private int cardY(int index) {
		return ui.view().gridCardY(index) + (queuedCount() > 0 ? SchemesPage.HELP_ROW : 0);
	}

	private static Text count(Category category) {
		int farms = category.farms().size();
		return farms == 1 ? Text.translatable("gui.the_dream_table.farm_count_one") : Text.translatable("gui.the_dream_table.farm_count", farms);
	}

	@Override
	public boolean click(double mouseX, double mouseY) {
		if (queuedCount() > 0) {
			int width = SchemesPage.halfWidth(ui.view());
			if (ui.view().hit(mouseX, mouseY, ui.view().collectionX(), ui.view().cardY(0), width, BUTTON_HEIGHT)) {
				if (ProjectState.locked(ui.handler())) {
					ui.message("gui.the_dream_table.farm_busy");
				} else {
					ui.actions().press(DreamTableScreenHandler.BUTTON_QUEUE_START);
				}
				return true;
			}
			if (ui.view().hit(mouseX, mouseY, SchemesPage.folderX(ui.view()), ui.view().cardY(0), width, BUTTON_HEIGHT)) {
				ui.actions().press(DreamTableScreenHandler.BUTTON_QUEUE_CLEAR);
				return true;
			}
		}
		for (int index = 0; index < CATEGORIES.length; index++) {
			if (ui.cardHit(mouseX, mouseY, ui.view().gridCardX(index), cardY(index))) {
				ui.go(DreamTableScreenHandler.PAGE_CATEGORY + index, DreamTableScreenHandler.BUTTON_CATEGORY + index);
				return true;
			}
		}
		return false;
	}

	@Override
	public int contentHeight() {
		int count = CATEGORIES.length;
		return ui.view().gridContentHeight(count) + (queuedCount() > 0 ? SchemesPage.HELP_ROW : 0);
	}

	@Override
	public Text subtitle() {
		return Text.translatable("gui.the_dream_table.choose");
	}

}
