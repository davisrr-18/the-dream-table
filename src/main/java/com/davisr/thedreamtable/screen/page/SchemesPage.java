package com.davisr.thedreamtable.screen.page;

import com.davisr.thedreamtable.farm.FarmOptions.FarmOption;
import com.davisr.thedreamtable.farm.FarmTemplates;
import com.davisr.thedreamtable.screen.DreamTableScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.List;

import static com.davisr.thedreamtable.screen.ui.Theme.*;

public final class SchemesPage implements TablePage {
	private static final int EMPTY_HEIGHT = 48;

	private final PageContext ui;

	public SchemesPage(PageContext ui) {
		this.ui = ui;
	}

	@Override
	public void draw(DrawContext context, int mouseX, int mouseY) {
		List<FarmOption> schemes = FarmTemplates.imported();
		if (schemes.isEmpty()) {
			int textY = ui.view().panelY() + HEADER + PAD + 8;
			for (OrderedText line : ui.painter().text().wrapLines(Text.translatable("gui.the_dream_table.schemes_empty"), CARD_WIDTH)) {
				context.drawText(ui.painter().text(), line, ui.view().cardX(), textY, HINT, false);
				textY += 12;
			}
		} else {
			boolean locked = ProjectState.locked(ui.handler());
			for (int index = 0; index < schemes.size(); index++) {
				FarmOption option = schemes.get(index);
				boolean selected = option.id().equals(ui.farm());
				FarmCard.draw(ui, context, option, ui.view().cardY(index), mouseX, mouseY, selected, !locked || selected);
			}
		}
		ui.button(context, Text.translatable("gui.the_dream_table.back"), backY(), mouseX, mouseY, true, false);
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
		if (ui.buttonHit(mouseX, mouseY, backY())) {
			ui.go(DreamTableScreenHandler.PAGE_SELECT, DreamTableScreenHandler.BUTTON_SCHEMES_BACK);
			return true;
		}
		return false;
	}

	@Override
	public int contentHeight() {
		return PAD + listHeight() + 10 + BUTTON_HEIGHT + 8;
	}

	@Override
	public Text subtitle() {
		return Text.translatable("gui.the_dream_table.schemes");
	}

	private int listHeight() {
		int count = FarmTemplates.imported().size();
		return count == 0 ? EMPTY_HEIGHT : count * CARD_HEIGHT + (count - 1) * CARD_GAP;
	}

	private int backY() {
		return ui.view().panelY() + HEADER + PAD + listHeight() + 10;
	}
}
