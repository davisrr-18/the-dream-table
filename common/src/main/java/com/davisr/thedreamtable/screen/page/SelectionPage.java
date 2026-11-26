package com.davisr.thedreamtable.screen.page;

import com.davisr.thedreamtable.farm.FarmOptions.Category;
import com.davisr.thedreamtable.screen.DreamTableScreenHandler;
import com.davisr.thedreamtable.screen.ui.Viewport;
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
			// Standard full-width buttons, stacked: two of them do not fit side by side.
			ui.buttonAt("queue_start", context, Text.translatable("gui.the_dream_table.queue_start", queued), ui.view().collectionX(), startY(ui.view()), mouseX, mouseY, !ProjectState.locked(ui.handler()), true);
			ui.buttonAt("queue_clear", context, Text.translatable("gui.the_dream_table.queue_clear"), ui.view().collectionX(), clearY(ui.view()), mouseX, mouseY, true, false);
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

	/** The two queue buttons and the gap under them. */
	static final int QUEUE_ROWS = 2 * BUTTON_HEIGHT + BUTTON_GAP + CARD_GAP;

	static int startY(Viewport view) {
		return view.cardY(0);
	}

	static int clearY(Viewport view) {
		return startY(view) + BUTTON_HEIGHT + BUTTON_GAP;
	}

	/** The cards sit under the queue buttons while a queue exists. */
	static int cardY(Viewport view, int index, boolean queued) {
		return view.gridCardY(index) + (queued ? QUEUE_ROWS : 0);
	}

	static int contentHeight(Viewport view, int count, boolean queued) {
		return view.gridContentHeight(count) + (queued ? QUEUE_ROWS : 0);
	}

	private int cardY(int index) {
		return cardY(ui.view(), index, queuedCount() > 0);
	}

	private static Text count(Category category) {
		int farms = category.farms().size();
		return farms == 1 ? Text.translatable("gui.the_dream_table.farm_count_one") : Text.translatable("gui.the_dream_table.farm_count", farms);
	}

	@Override
	public boolean click(double mouseX, double mouseY) {
		if (queuedCount() > 0) {
			if (ui.buttonHit(mouseX, mouseY, ui.view().collectionX(), startY(ui.view()))) {
				if (ProjectState.locked(ui.handler())) {
					ui.message("gui.the_dream_table.farm_busy");
				} else {
					ui.actions().press(DreamTableScreenHandler.BUTTON_QUEUE_START);
				}
				return true;
			}
			if (ui.buttonHit(mouseX, mouseY, ui.view().collectionX(), clearY(ui.view()))) {
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
		return contentHeight(ui.view(), CATEGORIES.length, queuedCount() > 0);
	}

	@Override
	public Text subtitle() {
		return Text.translatable("gui.the_dream_table.choose");
	}

}
