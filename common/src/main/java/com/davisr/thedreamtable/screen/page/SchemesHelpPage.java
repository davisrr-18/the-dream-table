package com.davisr.thedreamtable.screen.page;

import com.davisr.thedreamtable.farm.FarmInfo;
import com.davisr.thedreamtable.screen.DreamTableScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.List;

import static com.davisr.thedreamtable.screen.ui.Theme.*;

/**
 * How It Works on My Schematics: where to save files, which formats and size are read, how a server's list works and
 * why a file is greyed out. The text comes from the language files under {@code gui.the_dream_table.schemes_help}, laid
 * out like a farm's help page and scrolled by the viewport.
 */
public final class SchemesHelpPage implements TablePage {
	private static final int TEXT_GAP = 8;
	private static final String BULLET = "•";
	private static final String KEY = "gui.the_dream_table.schemes_help.";
	/** In reading order. Each heading is {@code KEY + name}; its bullets, one per line, are under {@code KEY + name + ".text"}. */
	private static final List<String> SECTIONS = List.of("where", "formats", "server", "greyed", "list");

	private final PageContext ui;
	private int laidOutWidth = -1;
	private FarmInfoText.Layout layout = FarmInfoText.Layout.EMPTY;

	public SchemesHelpPage(PageContext ui) {
		this.ui = ui;
	}

	@Override
	public void draw(DrawContext context, int mouseX, int mouseY) {
		ui.cardAt("schemes", context, new ItemStack(Items.BOOK), Text.translatable("gui.the_dream_table.schemes"), flowX(), ui.view().cardY(0), ui.view().flowWidth(), mouseX, mouseY, true, true);
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
			ui.go(DreamTableScreenHandler.PAGE_SCHEMES, DreamTableScreenHandler.BUTTON_SCHEMES_HELP_BACK);
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

	/** Wrapped once per width: the lines are asked for several times per frame. */
	private FarmInfoText.Layout layout() {
		int width = ui.view().flowWidth();
		if (width != laidOutWidth) {
			layout = FarmInfoText.layout(sections(), width, (text, wrapWidth) -> ui.painter().text().wrapLines(text, wrapWidth));
			laidOutWidth = width;
		}
		return layout;
	}

	private static List<FarmInfoText.Heading> sections() {
		return SECTIONS.stream().map(name -> new FarmInfoText.Heading(KEY + name, FarmInfo.lines(KEY + name + ".text"))).toList();
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
