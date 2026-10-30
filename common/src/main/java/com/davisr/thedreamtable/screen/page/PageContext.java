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
	private ControlId focusedControl;
	private ItemStack hoveredItem = ItemStack.EMPTY;
	private Text hoveredNote = Text.empty();

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

	/** The control that has keyboard focus and should draw a focus ring, or null when none should. */
	public void setFocusedControl(ControlId id) {
		this.focusedControl = id;
	}

	public void beginControls(int pageId, TablePage page) {
		this.pageId = pageId;
		this.page = page;
		this.controlIndex = 0;
		controls.clear();
		controlActions.clear();
		hoveredItem = ItemStack.EMPTY;
		hoveredNote = Text.empty();
	}

	/**
	 * Marks an item icon drawn at x, y (16 px) as the one the mouse is over, for icons that show no name of their own.
	 * The screen draws the item's name and the note under the mouse after the page.
	 */
	public void hoverItem(ItemStack stack, Text note, int x, int y, int mouseX, int mouseY) {
		if (!stack.isEmpty() && view.hit(mouseX, mouseY, x, y, 16, 16)) {
			hoveredItem = stack;
			hoveredNote = note == null ? Text.empty() : note;
		}
	}

	/** The item icon under the mouse this frame, or empty. */
	public ItemStack hoveredItem() {
		return hoveredItem;
	}

	/** The extra line to show under the hovered item's name, such as how many; may be empty. */
	public Text hoveredNote() {
		return hoveredNote;
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
		ControlId id = register(stableKey, label, Text.empty(), x, y, CARD_HEIGHT, selected, enabled);
		boolean hit = cardHit(mouseX, mouseY, x, y);
		painter.card(context, icon, label, x, y, hit, selected, enabled);
		focusRing(context, id, x, y, CARD_WIDTH, CARD_HEIGHT);
	}

	public void card(DrawContext context, ItemStack icon, Text label, Text detail, int y, int mouseX, int mouseY, boolean selected, boolean enabled) {
		card(null, context, icon, label, detail, y, mouseX, mouseY, selected, enabled);
	}

	public void card(String stableKey, DrawContext context, ItemStack icon, Text label, Text detail, int y, int mouseX, int mouseY, boolean selected, boolean enabled) {
		cardAt(stableKey, context, icon, label, detail, view.cardX(), y, mouseX, mouseY, selected, enabled);
	}

	public void cardAt(String stableKey, DrawContext context, ItemStack icon, Text label, Text detail, int x, int y, int mouseX, int mouseY, boolean selected, boolean enabled) {
		ControlId id = register(stableKey, label, detail, x, y, CARD_HEIGHT, selected, enabled);
		boolean hit = cardHit(mouseX, mouseY, x, y);
		painter.card(context, icon, label, detail, x, y, hit, selected, enabled);
		focusRing(context, id, x, y, CARD_WIDTH, CARD_HEIGHT);
	}

	public void button(DrawContext context, Text label, int y, int mouseX, int mouseY, boolean enabled, boolean primary) {
		button(null, context, label, y, mouseX, mouseY, enabled, primary);
	}

	public void button(String stableKey, DrawContext context, Text label, int y, int mouseX, int mouseY, boolean enabled, boolean primary) {
		buttonAt(stableKey, context, label, view.cardX(), y, mouseX, mouseY, enabled, primary);
	}

	public void buttonAt(String stableKey, DrawContext context, Text label, int x, int y, int mouseX, int mouseY, boolean enabled, boolean primary) {
		int width = spanWidth(x);
		x = spanX(x);
		ControlId id = register(stableKey, label, Text.empty(), Text.empty(), x, y, width, BUTTON_HEIGHT, false, enabled);
		boolean hit = view.hit(mouseX, mouseY, x, y, width, BUTTON_HEIGHT);
		painter.button(context, label, x, y, width, hit, enabled, primary);
		focusRing(context, id, x, y, width, BUTTON_HEIGHT);
	}

	/** The Back button of a card grid: it spans the whole collection width, from the first column to the last. */
	public void backButton(DrawContext context, int y, int mouseX, int mouseY) {
		buttonAt("back", context, Text.translatable("gui.the_dream_table.back"), view.collectionX(), y, mouseX, mouseY, true, false);
	}

	public boolean backHit(double mouseX, double mouseY, int y) {
		return buttonHit(mouseX, mouseY, view.collectionX(), y);
	}

	/** A button with a second, smaller line under its label: what it does, or why it is off. */
	public void button(String stableKey, DrawContext context, Text label, Text detail, int y, int mouseX, int mouseY, boolean enabled, boolean primary) {
		buttonAt(stableKey, context, label, detail, view.cardX(), y, mouseX, mouseY, enabled, primary);
	}

	/**
	 * A button with a second line at a given x. A disabled one narrates the detail as its reason, an enabled one as its
	 * state.
	 */
	public void buttonAt(String stableKey, DrawContext context, Text label, Text detail, int x, int y, int mouseX, int mouseY, boolean enabled, boolean primary) {
		int width = spanWidth(x);
		x = spanX(x);
		ControlId id = enabled ? register(stableKey, label, detail, Text.empty(), x, y, width, BUTTON_HEIGHT, false, true)
				: register(stableKey, label, Text.empty(), detail, x, y, width, BUTTON_HEIGHT, false, false);
		boolean hit = view.hit(mouseX, mouseY, x, y, width, BUTTON_HEIGHT);
		painter.button(context, label, detail, x, y, width, hit, enabled, primary);
		focusRing(context, id, x, y, width, BUTTON_HEIGHT);
	}

	/**
	 * A slider the keyboard can focus and adjust: Left and Right step it, Home and End jump to its ends (the screen
	 * routes those keys to {@link TablePage#adjust}). Enter or Space runs {@code activate}. The caller draws the track.
	 */
	public void sliderAt(String stableKey, DrawContext context, Text label, Text value, UiRect bounds, Runnable activate) {
		int index = controlIndex++;
		ControlId id = ControlId.of("page." + pageId, stableKey == null ? Integer.toString(index) : stableKey);
		controls.add(new ControlSpec(id, ControlGroup.CONTENT, bounds, label, value, true, false, Text.empty(), bounds.y(), bounds.x()));
		controlActions.put(id, activate);
		focusRing(context, id, bounds.x(), bounds.y(), bounds.width(), bounds.height());
	}

	/** The id a control registered under {@code stableKey} has on the current page. */
	public ControlId idOf(String stableKey) {
		return ControlId.of("page." + pageId, stableKey);
	}

	public void destructiveButton(DrawContext context, Text label, int y, int mouseX, int mouseY, boolean enabled) {
		destructiveButton(null, context, label, y, mouseX, mouseY, enabled);
	}

	public void destructiveButton(String stableKey, DrawContext context, Text label, int y, int mouseX, int mouseY, boolean enabled) {
		destructiveButtonAt(stableKey, context, label, view.cardX(), y, mouseX, mouseY, enabled);
	}

	public void destructiveButtonAt(String stableKey, DrawContext context, Text label, int x, int y, int mouseX, int mouseY, boolean enabled) {
		int width = spanWidth(x);
		x = spanX(x);
		ControlId id = register(stableKey, label, Text.empty(), Text.empty(), x, y, width, BUTTON_HEIGHT, false, enabled);
		boolean hit = view.hit(mouseX, mouseY, x, y, width, BUTTON_HEIGHT);
		painter.destructiveButton(context, label, x, y, width, hit, enabled);
		focusRing(context, id, x, y, width, BUTTON_HEIGHT);
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
		focusRing(context, id, bounds.x(), bounds.y(), bounds.width(), bounds.height());
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
		return view.hit(mouseX, mouseY, spanX(x), y, spanWidth(x), BUTTON_HEIGHT);
	}

	/**
	 * A button placed at the left edge of the collection or at the centered flow column spans the whole collection
	 * width, so stacked buttons line up with the Back button; cards keep their own width.
	 */
	private boolean spans(int x) {
		return x == view.collectionX() || x == view.flowX();
	}

	private int spanX(int x) {
		return spans(x) ? view.collectionX() : x;
	}

	private int spanWidth(int x) {
		return spans(x) ? view.collectionWidth() : CARD_WIDTH;
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

	public void hint(String key, Object... args) {
		actions.hint(Text.translatable(key, args));
	}

	private void focusRing(DrawContext context, ControlId id, int x, int y, int width, int height) {
		if (id.equals(focusedControl)) {
			painter.focusRing(context, x, y, width, height);
		}
	}

	private ControlId register(String stableKey, Text label, Text state, int x, int y, int height, boolean selected, boolean enabled) {
		return register(stableKey, label, state, Text.empty(), x, y, height, selected, enabled);
	}

	private ControlId register(String stableKey, Text label, Text state, Text disabledReason, int x, int y, int height, boolean selected, boolean enabled) {
		return register(stableKey, label, state, disabledReason, x, y, CARD_WIDTH, height, selected, enabled);
	}

	private ControlId register(String stableKey, Text label, Text state, Text disabledReason, int x, int y, int width, int height, boolean selected, boolean enabled) {
		int index = controlIndex++;
		ControlId id = ControlId.of("page." + pageId, stableKey == null ? Integer.toString(index) : stableKey);
		UiRect bounds = new UiRect(x, y, width, height);
		ControlSpec spec = new ControlSpec(id, ControlGroup.CONTENT, bounds, label, state, enabled, selected,
				disabledReason, y, x);
		controls.add(spec);
		controlActions.put(id, () -> page.click(bounds.x() + bounds.width() / 2.0, bounds.y() + bounds.height() / 2.0));
		return id;
	}
}
