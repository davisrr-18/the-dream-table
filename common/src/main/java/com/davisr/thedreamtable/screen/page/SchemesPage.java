package com.davisr.thedreamtable.screen.page;

import com.davisr.thedreamtable.farm.FarmOptions.FarmOption;
import com.davisr.thedreamtable.farm.BuildPace;
import com.davisr.thedreamtable.farm.FarmTemplates;
import com.davisr.thedreamtable.farm.ImportedStructures;
import com.davisr.thedreamtable.screen.DreamTableScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.List;

import static com.davisr.thedreamtable.screen.ui.Theme.*;

/**
 * My Schematics: the farms read from schematic files, then the files that can't be used, greyed out. A file that can't
 * be used says why in the detail line of its card, which scrolls when it is longer than the card.
 */
public final class SchemesPage implements TablePage {
	private static final int EMPTY_HEIGHT = 48;
	private static final int REJECTED_NOTE = 24;

	private final PageContext ui;

	public SchemesPage(PageContext ui) {
		this.ui = ui;
	}

	@Override
	public void draw(DrawContext context, int mouseX, int mouseY) {
		List<FarmOption> schemes = FarmTemplates.imported();
		List<ImportedStructures.Rejected> rejected = FarmTemplates.rejected();
		if (schemes.isEmpty() && rejected.isEmpty()) {
			ui.collectionNote(context, Text.translatable("gui.the_dream_table.schemes_empty"), ui.view().cardY(0), HINT);
		}
		boolean locked = ProjectState.locked(ui.handler());
		for (int index = 0; index < schemes.size(); index++) {
			FarmOption option = schemes.get(index);
			boolean selected = option.id().equals(ui.farm());
			int x = cardX(ui.view(), index);
			int y = cardY(ui.view(), index);
			drawCard(option, x, y, context, mouseX, mouseY, selected, !locked || selected);
		}
		if (!rejected.isEmpty()) {
			ui.collectionNote(context, Text.translatable("gui.the_dream_table.schemes_rejected"), rejectedNoteY(), HINT);
			for (int index = 0; index < rejected.size(); index++) {
				ImportedStructures.Rejected file = rejected.get(index);
				int x = cardX(ui.view(), index);
				int y = rejectedY(index);
				ui.cardAt(null, context, new ItemStack(Items.BARRIER), Text.literal(file.name()), file.reason().text(), x, y, mouseX, mouseY, false, false);
			}
		}
	}

	@Override
	public boolean click(double mouseX, double mouseY) {
		List<FarmOption> schemes = FarmTemplates.imported();
		for (int index = 0; index < schemes.size(); index++) {
			if (ui.cardHit(mouseX, mouseY, cardX(ui.view(), index), cardY(ui.view(), index))) {
				String id = schemes.get(index).id();
				if (!id.equals(ui.farm()) && ProjectState.locked(ui.handler())) {
					ui.message("gui.the_dream_table.farm_busy");
					return true;
				}
				ui.go(id, DreamTableScreenHandler.PAGE_WORK, DreamTableScreenHandler.BUTTON_SCHEME + index);
				return true;
			}
		}
		return false;
	}

	@Override
	public int contentHeight() {
		return PAD + listHeight() + 8;
	}

	@Override
	public Text subtitle() {
		return Text.translatable("gui.the_dream_table.schemes");
	}

	private int listHeight() {
		return listHeight(ui.view(), FarmTemplates.imported().size(), FarmTemplates.rejected().size());
	}

	private int rejectedNoteY() {
		return rejectedNoteY(ui.view(), FarmTemplates.imported().size());
	}

	private int rejectedY(int index) {
		return rejectedY(ui.view(), index, FarmTemplates.imported().size());
	}

	private void drawCard(FarmOption option, int x, int y, DrawContext context, int mouseX, int mouseY, boolean selected, boolean enabled) {
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

	static int cardX(com.davisr.thedreamtable.screen.ui.Viewport view, int index) {
		return view.gridCardX(index);
	}

	static int cardY(com.davisr.thedreamtable.screen.ui.Viewport view, int index) {
		return view.gridCardY(index);
	}

	static int cardsHeight(com.davisr.thedreamtable.screen.ui.Viewport view, int count) {
		int rows = rows(view, count);
		return rows == 0 ? 0 : rows * CARD_HEIGHT + (rows - 1) * CARD_GAP;
	}

	static int rejectedNoteY(com.davisr.thedreamtable.screen.ui.Viewport view, int farms) {
		return view.cardY(0) + cardsHeight(view, farms) + (farms > 0 ? CARD_GAP : 0) + 2;
	}

	static int rejectedY(com.davisr.thedreamtable.screen.ui.Viewport view, int index, int farms) {
		return rejectedNoteY(view, farms) - 2 + REJECTED_NOTE + (index / columns(view)) * (CARD_HEIGHT + CARD_GAP);
	}

	static int listHeight(com.davisr.thedreamtable.screen.ui.Viewport view, int farms, int rejected) {
		if (farms == 0 && rejected == 0) {
			return EMPTY_HEIGHT;
		}
		int height = cardsHeight(view, farms);
		if (rejected > 0) {
			height += (farms > 0 ? CARD_GAP : 0) + REJECTED_NOTE + cardsHeight(view, rejected);
		}
		return height;
	}

	private static int rows(com.davisr.thedreamtable.screen.ui.Viewport view, int count) {
		return (Math.max(0, count) + columns(view) - 1) / columns(view);
	}

	private static int columns(com.davisr.thedreamtable.screen.ui.Viewport view) {
		return view.gridCardX(0) == view.gridCardX(1) ? 1 : 2;
	}

}
