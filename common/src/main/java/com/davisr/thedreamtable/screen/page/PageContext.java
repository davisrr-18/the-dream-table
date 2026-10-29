package com.davisr.thedreamtable.screen.page;

import com.davisr.thedreamtable.farm.FarmTemplate;
import com.davisr.thedreamtable.farm.FarmTemplates;
import com.davisr.thedreamtable.screen.DreamTableScreenHandler;
import com.davisr.thedreamtable.screen.ui.Painter;
import com.davisr.thedreamtable.screen.ui.Viewport;
import com.davisr.thedreamtable.screen.ui.ControlGroup;
import com.davisr.thedreamtable.screen.ui.ControlId;
import com.davisr.thedreamtable.screen.ui.ControlSpec;
import com.davisr.thedreamtable.screen.ui.layout.UiRect;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.davisr.thedreamtable.screen.ui.Theme.*;

/** Shared access for pages: the synced handler, the layout, the painter, and the screen actions. */
public final class PageContext {
	private final DreamTableScreenHandler handler;
	private final Viewport view;
	private final Painter painter;
	private final ScreenActions actions;
	private final List<ControlSpec> controls = new ArrayList<>();
	private final Map<ControlId, Runnable> controlActions = new LinkedHashMap<>();
	private TablePage page;
	private int pageId;
	private int controlIndex;

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

	public void beginControls(int pageId, TablePage page) {
		this.pageId = pageId;
		this.page = page;
		this.controlIndex = 0;
		controls.clear();
		controlActions.clear();
	}

	public List<ControlSpec> controls() {
		return List.copyOf(controls);
	}

	public Runnable action(ControlId id) {
		return controlActions.getOrDefault(id, () -> { });
	}

	public void card(DrawContext context, ItemStack icon, Text label, int y, int mouseX, int mouseY, boolean selected, boolean enabled) {
		card(null, context, icon, label, y, mouseX, mouseY, selected, enabled);
	}

	public void card(String stableKey, DrawContext context, ItemStack icon, Text label, int y, int mouseX, int mouseY, boolean selected, boolean enabled) {
		cardAt(stableKey, context, icon, label, view.cardX(), y, mouseX, mouseY, selected, enabled);
	}

	public void cardAt(String stableKey, DrawContext context, ItemStack icon, Text label, int x, int y, int mouseX, int mouseY, boolean selected, boolean enabled) {
		register(stableKey, label, Text.empty(), x, y, CARD_HEIGHT, selected, enabled);
		boolean hit = cardHit(mouseX, mouseY, x, y);
		painter.card(context, icon, label, x, y, hit, selected, enabled);
	}

	public void card(DrawContext context, ItemStack icon, Text label, Text detail, int y, int mouseX, int mouseY, boolean selected, boolean enabled) {
		card(null, context, icon, label, detail, y, mouseX, mouseY, selected, enabled);
	}

	public void card(String stableKey, DrawContext context, ItemStack icon, Text label, Text detail, int y, int mouseX, int mouseY, boolean selected, boolean enabled) {
		cardAt(stableKey, context, icon, label, detail, view.cardX(), y, mouseX, mouseY, selected, enabled);
	}

	public void cardAt(String stableKey, DrawContext context, ItemStack icon, Text label, Text detail, int x, int y, int mouseX, int mouseY, boolean selected, boolean enabled) {
		register(stableKey, label, detail, x, y, CARD_HEIGHT, selected, enabled);
		boolean hit = cardHit(mouseX, mouseY, x, y);
		painter.card(context, icon, label, detail, x, y, hit, selected, enabled);
	}

	public void button(DrawContext context, Text label, int y, int mouseX, int mouseY, boolean enabled, boolean primary) {
		button(null, context, label, y, mouseX, mouseY, enabled, primary);
	}

	public void button(String stableKey, DrawContext context, Text label, int y, int mouseX, int mouseY, boolean enabled, boolean primary) {
		buttonAt(stableKey, context, label, view.cardX(), y, mouseX, mouseY, enabled, primary);
	}

	public void buttonAt(String stableKey, DrawContext context, Text label, int x, int y, int mouseX, int mouseY, boolean enabled, boolean primary) {
		register(stableKey, label, Text.empty(), x, y, BUTTON_HEIGHT, false, enabled);
		boolean hit = buttonHit(mouseX, mouseY, x, y);
		painter.button(context, label, x, y, hit, enabled, primary);
	}

	/** A button with a second, smaller line under its label: what it does, or why it is off. */
	public void button(String stableKey, DrawContext context, Text label, Text detail, int y, int mouseX, int mouseY, boolean enabled, boolean primary) {
		register(stableKey, label, detail, view.cardX(), y, BUTTON_HEIGHT, false, enabled);
		boolean hit = buttonHit(mouseX, mouseY, view.cardX(), y);
		painter.button(context, label, detail, view.cardX(), y, hit, enabled, primary);
	}

	public void destructiveButton(DrawContext context, Text label, int y, int mouseX, int mouseY, boolean enabled) {
		destructiveButton(null, context, label, y, mouseX, mouseY, enabled);
	}

	public void destructiveButton(String stableKey, DrawContext context, Text label, int y, int mouseX, int mouseY, boolean enabled) {
		destructiveButtonAt(stableKey, context, label, view.cardX(), y, mouseX, mouseY, enabled);
	}

	public void destructiveButtonAt(String stableKey, DrawContext context, Text label, int x, int y, int mouseX, int mouseY, boolean enabled) {
		register(stableKey, label, Text.empty(), x, y, BUTTON_HEIGHT, false, enabled);
		boolean hit = buttonHit(mouseX, mouseY, x, y);
		painter.destructiveButton(context, label, x, y, hit, enabled);
	}

	public void chip(String stableKey, DrawContext context, Text label, int y, int width, int height, int mouseX, int mouseY) {
		chipAt(stableKey, context, label, view.cardX(), y, width, height, mouseX, mouseY);
	}

	public void chipAt(String stableKey, DrawContext context, Text label, int x, int y, int width, int height, int mouseX, int mouseY) {
		int index = controlIndex++;
		ControlId id = ControlId.of("page." + pageId, stableKey == null ? Integer.toString(index) : stableKey);
		UiRect bounds = new UiRect(x, y, width, height);
		controls.add(new ControlSpec(id, ControlGroup.CONTENT, bounds, label, Text.empty(), true, false, Text.empty(), y, bounds.x()));
		controlActions.put(id, () -> page.click(bounds.x() + bounds.width() / 2.0, bounds.y() + bounds.height() / 2.0));
		boolean hit = view.hit(mouseX, mouseY, bounds.x(), bounds.y(), bounds.width(), bounds.height());
		painter.chip(context, label, bounds.x(), bounds.y(), bounds.width(), bounds.height(), hit);
	}

	/** Draws a non-interactive note centered across the responsive collection area. */
	public int collectionNote(DrawContext context, Text text, int y, int color) {
		return centeredNote(context, text, y, view.collectionX(), view.collectionWidth(), color);
	}

	/** Draws a non-interactive note centered within the current single-card workflow. */
	public int flowNote(DrawContext context, Text text, int y, int color) {
		return centeredNote(context, text, y, view.flowX(), CARD_WIDTH, color);
	}

	private int centeredNote(DrawContext context, Text text, int y, int x, int width, int color) {
		for (OrderedText line : painter.text().wrapLines(text, width)) {
			context.drawText(painter.text(), line, x + (width - painter.text().getWidth(line)) / 2, y, color, false);
			y += 11;
		}
		return y;
	}

	public boolean cardHit(double mouseX, double mouseY, int y) {
		return cardHit(mouseX, mouseY, view.cardX(), y);
	}

	public boolean cardHit(double mouseX, double mouseY, int x, int y) {
		return view.hit(mouseX, mouseY, x, y, CARD_WIDTH, CARD_HEIGHT);
	}

	public boolean buttonHit(double mouseX, double mouseY, int y) {
		return buttonHit(mouseX, mouseY, view.cardX(), y);
	}

	public boolean buttonHit(double mouseX, double mouseY, int x, int y) {
		return view.hit(mouseX, mouseY, x, y, CARD_WIDTH, BUTTON_HEIGHT);
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

	private void register(String stableKey, Text label, Text state, int x, int y, int height, boolean selected, boolean enabled) {
		int index = controlIndex++;
		ControlId id = ControlId.of("page." + pageId, stableKey == null ? Integer.toString(index) : stableKey);
		UiRect bounds = new UiRect(x, y, CARD_WIDTH, height);
		ControlSpec spec = new ControlSpec(id, ControlGroup.CONTENT, bounds, label, state, enabled, selected,
				enabled ? Text.empty() : Text.translatable("gui.the_dream_table.narration.disabled"), y, x);
		controls.add(spec);
		controlActions.put(id, () -> page.click(bounds.x() + bounds.width() / 2.0, bounds.y() + bounds.height() / 2.0));
	}
}
