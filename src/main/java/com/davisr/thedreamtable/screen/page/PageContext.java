package com.davisr.thedreamtable.screen.page;

import com.davisr.thedreamtable.farm.FarmTemplate;
import com.davisr.thedreamtable.farm.FarmTemplates;
import com.davisr.thedreamtable.screen.DreamTableScreenHandler;
import com.davisr.thedreamtable.screen.ui.Painter;
import com.davisr.thedreamtable.screen.ui.Viewport;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.List;

import static com.davisr.thedreamtable.screen.ui.Theme.*;

/** Shared access for pages: the synced handler, the layout, the painter, and the screen actions. */
public final class PageContext {
	private final DreamTableScreenHandler handler;
	private final Viewport view;
	private final Painter painter;
	private final ScreenActions actions;
	private List<Text> tooltip;

	public PageContext(DreamTableScreenHandler handler, Viewport view, Painter painter, ScreenActions actions) {
		this.handler = handler;
		this.view = view;
		this.painter = painter;
		this.actions = actions;
	}

	public DreamTableScreenHandler handler() {
		return handler;
	}

	public Viewport view() {
		return view;
	}

	public Painter painter() {
		return painter;
	}

	public ScreenActions actions() {
		return actions;
	}

	public String farm() {
		return handler.getSelectedFarm();
	}

	public FarmTemplate template() {
		return FarmTemplates.get(farm());
	}

	public int materialCount() {
		FarmTemplate template = template();
		return template == null ? 0 : template.materials().size();
	}

	public void card(DrawContext context, ItemStack icon, Text label, int y, int mouseX, int mouseY, boolean selected, boolean enabled) {
		boolean hit = cardHit(mouseX, mouseY, y);
		painter.card(context, icon, label, view.cardX(), y, hit, selected, enabled);
		if (hit && !painter.fits(label, CARD_TEXT_WIDTH)) {
			tooltip(label);
		}
	}

	public void card(DrawContext context, ItemStack icon, Text label, Text detail, int y, int mouseX, int mouseY, boolean selected, boolean enabled) {
		boolean hit = cardHit(mouseX, mouseY, y);
		painter.card(context, icon, label, detail, view.cardX(), y, hit, selected, enabled);
		if (hit && (!painter.fits(label, CARD_TEXT_WIDTH) || !painter.fits(detail, CARD_TEXT_WIDTH))) {
			tooltip(label, detail);
		}
	}

	public void button(DrawContext context, Text label, int y, int mouseX, int mouseY, boolean enabled, boolean primary) {
		boolean hit = buttonHit(mouseX, mouseY, y);
		painter.button(context, label, view.cardX(), y, hit, enabled, primary);
		if (hit && !painter.fits(label, CARD_WIDTH - 16)) {
			tooltip(label);
		}
	}

	/** Text shown by the mouse after the page is drawn, for labels cut short with "...". The last one asked for wins. */
	public void tooltip(Text... lines) {
		tooltip = List.of(lines);
	}

	/** The tooltip asked for during this frame, once. */
	public List<Text> takeTooltip() {
		List<Text> lines = tooltip;
		tooltip = null;
		return lines;
	}

	public boolean cardHit(double mouseX, double mouseY, int y) {
		return view.hit(mouseX, mouseY, view.cardX(), y, CARD_WIDTH, CARD_HEIGHT);
	}

	public boolean buttonHit(double mouseX, double mouseY, int y) {
		return view.hit(mouseX, mouseY, view.cardX(), y, CARD_WIDTH, BUTTON_HEIGHT);
	}

	/** Shows the next page right away and asks the server to switch to it. */
	public void go(String farm, int page, int buttonId) {
		handler.preview(farm, page);
		actions.press(buttonId);
	}

	public void go(int page, int buttonId) {
		go(farm(), page, buttonId);
	}

	public void message(String key) {
		actions.message(Text.translatable(key));
	}
}
