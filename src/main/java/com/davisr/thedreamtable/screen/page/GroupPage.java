package com.davisr.thedreamtable.screen.page;

import com.davisr.thedreamtable.farm.FarmOptions;
import com.davisr.thedreamtable.farm.FarmOptions.FarmOption;
import com.davisr.thedreamtable.farm.FarmOptions.Group;
import com.davisr.thedreamtable.screen.DreamTableScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.List;

import static com.davisr.thedreamtable.screen.ui.Theme.*;

/** The sizes of one farm kind, smallest first, each drawn as a {@link FarmCard} with its size tag, blocks and time. */
public final class GroupPage implements TablePage {
	private final PageContext ui;

	public GroupPage(PageContext ui) {
		this.ui = ui;
	}

	public static boolean isGroupPage(int page) {
		return Group.byIndex(page - DreamTableScreenHandler.PAGE_GROUP).isPresent();
	}

	/** Opens the farm, unless another project is still in progress at this table. */
	static void choose(PageContext ui, FarmOption option) {
		if (!option.id().equals(ui.farm()) && ProjectState.locked(ui.handler())) {
			ui.message("gui.the_dream_table.farm_busy");
			return;
		}
		ui.go(option.id(), DreamTableScreenHandler.PAGE_WORK, FarmOptions.ALL.indexOf(option));
	}

	private Group group() {
		return Group.byIndex(ui.handler().getPage() - DreamTableScreenHandler.PAGE_GROUP).orElse(Group.SUGAR_CANE_FARMS);
	}

	@Override
	public void draw(DrawContext context, int mouseX, int mouseY) {
		List<FarmOption> sizes = group().sizes();
		boolean locked = ProjectState.locked(ui.handler());
		for (int index = 0; index < sizes.size(); index++) {
			FarmOption option = sizes.get(index);
			boolean selected = option.id().equals(ui.farm());
			FarmCard.draw(ui, context, option, ui.view().cardY(index), mouseX, mouseY, selected, !locked || selected);
		}
		ui.button(context, Text.translatable("gui.the_dream_table.back"), backY(), mouseX, mouseY, true, false);
	}

	@Override
	public boolean click(double mouseX, double mouseY) {
		List<FarmOption> sizes = group().sizes();
		for (int index = 0; index < sizes.size(); index++) {
			if (ui.cardHit(mouseX, mouseY, ui.view().cardY(index))) {
				choose(ui, sizes.get(index));
				return true;
			}
		}
		if (ui.buttonHit(mouseX, mouseY, backY())) {
			int category = FarmOptions.Category.of(group()).map(Enum::ordinal).orElse(0);
			ui.go(DreamTableScreenHandler.PAGE_CATEGORY + category, DreamTableScreenHandler.BUTTON_GROUP_BACK);
			return true;
		}
		return false;
	}

	@Override
	public int contentHeight() {
		int count = group().sizes().size();
		return PAD + count * CARD_HEIGHT + (count - 1) * CARD_GAP + 6 + BUTTON_HEIGHT + 4;
	}

	@Override
	public Text subtitle() {
		return group().title();
	}

	private int backY() {
		return ui.view().cardY(group().sizes().size() - 1) + CARD_HEIGHT + 6;
	}
}
