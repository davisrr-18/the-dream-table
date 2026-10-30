package com.davisr.thedreamtable.screen.page;

import com.davisr.thedreamtable.farm.FarmInfo;
import com.davisr.thedreamtable.farm.FarmOptions;
import com.davisr.thedreamtable.farm.FarmOptions.FarmOption;
import com.davisr.thedreamtable.screen.DreamTableScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import static com.davisr.thedreamtable.screen.ui.Theme.*;

/**
 * How the selected built-in farm works, when it works, what it produces and some tips, from the language files
 * ({@link FarmInfo}). Opened from the Work page; the text wraps to the card width and the viewport scrolls it.
 */
public final class FarmInfoPage implements TablePage {
	private static final int TEXT_GAP = 8;
	private static final String BULLET = "•";

	private final PageContext ui;
	private String laidOutFarm;
	private int laidOutWidth;
	private FarmInfoText.Layout layout = FarmInfoText.Layout.EMPTY;

	public FarmInfoPage(PageContext ui) {
		this.ui = ui;
	}

	@Override
	public void draw(DrawContext context, int mouseX, int mouseY) {
		FarmOption option = FarmOptions.byId(ui.farm()).orElse(null);
		ItemStack icon = option == null ? new ItemStack(Items.STRUCTURE_BLOCK) : option.icon().getDefaultStack();
		Text name = option == null ? Text.literal(ui.farm()) : option.title();
		ui.cardAt("farm", context, icon, name, flowX(), ui.view().cardY(0), ui.view().flowWidth(), mouseX, mouseY, true, true);
		int textY = textY();
		for (FarmInfoText.Row row : layout().rows()) {
			int y = textY + row.top();
			if (row.bullet()) {
				context.drawText(ui.painter().text(), BULLET, flowX(), y, HINT, false);
			}
			context.drawText(ui.painter().text(), row.text(), flowX() + row.indent(), y, row.color(), false);
		}
		ui.buttonAt("back", context, Text.translatable("gui.the_dream_table.back"), flowX(), backY(), mouseX, mouseY, true, false);
	}

	@Override
	public boolean click(double mouseX, double mouseY) {
		if (ui.buttonHit(mouseX, mouseY, flowX(), backY())) {
			ui.go(DreamTableScreenHandler.PAGE_WORK, DreamTableScreenHandler.BUTTON_FARM_INFO_BACK);
			return true;
		}
		return false;
	}

	@Override
	public int contentHeight() {
		return backY() - ui.view().cardY(0) + BUTTON_HEIGHT + PAD + 8;
	}

	@Override
	public Text subtitle() {
		return Text.translatable("gui.the_dream_table.info_page");
	}

	/** Wrapped once per farm: the lines are asked for several times per frame. The screen is rebuilt on a language change. */
	private FarmInfoText.Layout layout() {
		String farm = ui.farm();
		int width = ui.view().flowWidth();
		if (!farm.equals(laidOutFarm) || width != laidOutWidth) {
			layout = FarmInfoText.layout(FarmInfo.sections(farm), width, (text, wrapWidth) -> ui.painter().text().wrapLines(text, wrapWidth));
			laidOutFarm = farm;
			laidOutWidth = width;
		}
		return layout;
	}

	private int textY() {
		return ui.view().cardY(0) + CARD_HEIGHT + TEXT_GAP;
	}

	private int backY() {
		return textY() + layout().height() + TEXT_GAP;
	}

	private int flowX() {
		return ui.view().flowX();
	}
}
