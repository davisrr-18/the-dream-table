package com.davisr.thedreamtable.screen.page;

import com.davisr.thedreamtable.farm.FarmTemplate;
import com.davisr.thedreamtable.farm.FarmTemplates;
import com.davisr.thedreamtable.screen.DreamTableScreenHandler;
import com.davisr.thedreamtable.screen.ui.Painter;
import com.davisr.thedreamtable.screen.ui.Viewport;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import static com.davisr.thedreamtable.screen.ui.Theme.*;

/** Shared access for pages: the synced handler, the layout, the painter, and the screen actions. */
public final class PageContext {
	private final DreamTableScreenHandler handler;
	private final Viewport view;
	private final Painter painter;
	private final ScreenActions actions;

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
		painter.card(context, icon, label, view.cardX(), y, cardHit(mouseX, mouseY, y), selected, enabled);
	}

	public void card(DrawContext context, ItemStack icon, Text label, Text detail, int y, int mouseX, int mouseY, boolean selected, boolean enabled) {
		painter.card(context, icon, label, detail, view.cardX(), y, cardHit(mouseX, mouseY, y), selected, enabled);
	}

	public void button(DrawContext context, Text label, int y, int mouseX, int mouseY, boolean enabled, boolean primary) {
		painter.button(context, label, view.cardX(), y, buttonHit(mouseX, mouseY, y), enabled, primary);
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
