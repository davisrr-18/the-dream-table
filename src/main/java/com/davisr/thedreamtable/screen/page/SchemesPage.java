package com.davisr.thedreamtable.screen.page;

import com.davisr.thedreamtable.farm.FarmOptions.FarmOption;
import com.davisr.thedreamtable.farm.FarmTemplates;
import com.davisr.thedreamtable.farm.ImportNote;
import com.davisr.thedreamtable.farm.ImportedStructures;
import com.davisr.thedreamtable.screen.DreamTableScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

import static com.davisr.thedreamtable.screen.ui.Theme.*;

/**
 * My Schematics: the farms read from schematic files, then the files that can't be used, greyed out with the reason.
 * Pointing at a farm with warnings, or at a file that can't be used, shows the full text.
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
			note(context, Text.translatable("gui.the_dream_table.schemes_empty"), ui.view().cardY(0));
		}
		boolean locked = ProjectState.locked(ui.handler());
		for (int index = 0; index < schemes.size(); index++) {
			FarmOption option = schemes.get(index);
			boolean selected = option.id().equals(ui.farm());
			int y = ui.view().cardY(index);
			FarmCard.draw(ui, context, option, y, mouseX, mouseY, selected, !locked || selected);
			List<ImportNote> notes = FarmTemplates.notes(option.id());
			if (!notes.isEmpty() && ui.cardHit(mouseX, mouseY, y)) {
				List<Text> lines = new ArrayList<>();
				lines.add(option.title());
				notes.forEach(note -> lines.add(note.text()));
				ui.tooltip(lines.toArray(Text[]::new));
			}
		}
		if (!rejected.isEmpty()) {
			note(context, Text.translatable("gui.the_dream_table.schemes_rejected"), rejectedNoteY());
			for (int index = 0; index < rejected.size(); index++) {
				ImportedStructures.Rejected file = rejected.get(index);
				int y = rejectedY(index);
				ui.card(context, new ItemStack(Items.BARRIER), Text.literal(file.name()), file.reason().text(), y, mouseX, mouseY, false, false);
				if (ui.cardHit(mouseX, mouseY, y)) {
					ui.tooltip(Text.literal(file.name()), file.reason().text());
				}
			}
		}
	}

	private void note(DrawContext context, Text text, int y) {
		for (OrderedText line : ui.painter().text().wrapLines(text, CARD_WIDTH)) {
			context.drawText(ui.painter().text(), line, ui.view().cardX(), y, HINT, false);
			y += 11;
		}
	}

	@Override
	public boolean click(double mouseX, double mouseY) {
		List<FarmOption> schemes = FarmTemplates.imported();
		for (int index = 0; index < schemes.size(); index++) {
			if (ui.cardHit(mouseX, mouseY, ui.view().cardY(index))) {
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

	private static int cardsHeight(int count) {
		return count == 0 ? 0 : count * CARD_HEIGHT + (count - 1) * CARD_GAP;
	}

	private int listHeight() {
		int farms = FarmTemplates.imported().size();
		int rejected = FarmTemplates.rejected().size();
		if (farms == 0 && rejected == 0) {
			return EMPTY_HEIGHT;
		}
		int height = cardsHeight(farms);
		if (rejected > 0) {
			height += (farms > 0 ? CARD_GAP : 0) + REJECTED_NOTE + cardsHeight(rejected);
		}
		return height;
	}

	private int rejectedNoteY() {
		int farms = FarmTemplates.imported().size();
		return ui.view().cardY(0) + cardsHeight(farms) + (farms > 0 ? CARD_GAP : 0) + 2;
	}

	private int rejectedY(int index) {
		return rejectedNoteY() - 2 + REJECTED_NOTE + index * (CARD_HEIGHT + CARD_GAP);
	}

}
