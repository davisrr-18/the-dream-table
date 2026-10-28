package com.davisr.thedreamtable.screen;

import com.davisr.thedreamtable.client.DreamTableHologram;
import com.davisr.thedreamtable.client.DreamTableKeys;
import com.davisr.thedreamtable.farm.FarmTemplates;
import com.davisr.thedreamtable.screen.page.MaterialsPage;
import com.davisr.thedreamtable.screen.page.CategoryPage;
import com.davisr.thedreamtable.screen.page.FarmPage;
import com.davisr.thedreamtable.screen.page.LoadedFarmsPage;
import com.davisr.thedreamtable.screen.page.InvitesPage;
import com.davisr.thedreamtable.screen.page.AlertsPage;
import com.davisr.thedreamtable.client.LoadedFarmsView;
import com.davisr.thedreamtable.screen.page.OptionsPage;
import com.davisr.thedreamtable.screen.page.SpeedPage;
import com.davisr.thedreamtable.screen.page.PageContext;
import com.davisr.thedreamtable.screen.page.ProjectState;
import com.davisr.thedreamtable.screen.page.RequirementsPage;
import com.davisr.thedreamtable.screen.page.SchemesPage;
import com.davisr.thedreamtable.screen.page.ScreenActions;
import com.davisr.thedreamtable.screen.page.SelectionPage;
import com.davisr.thedreamtable.screen.page.SwapPage;
import com.davisr.thedreamtable.screen.page.TablePage;
import com.davisr.thedreamtable.screen.page.WorkPage;
import com.davisr.thedreamtable.screen.page.PageRoutes;
import com.davisr.thedreamtable.screen.page.PrimaryTab;
import com.davisr.thedreamtable.screen.ui.ControlGroup;
import com.davisr.thedreamtable.screen.ui.ControlId;
import com.davisr.thedreamtable.screen.ui.ControlRegistry;
import com.davisr.thedreamtable.screen.ui.ControlSpec;
import com.davisr.thedreamtable.screen.ui.Painter;
import com.davisr.thedreamtable.screen.ui.SearchBox;
import com.davisr.thedreamtable.screen.ui.UiControl;
import com.davisr.thedreamtable.screen.ui.Viewport;
import com.davisr.thedreamtable.screen.ui.layout.ScreenGeometry;
import com.davisr.thedreamtable.screen.ui.layout.LayoutMode;
import com.davisr.thedreamtable.screen.ui.layout.UiRect;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static com.davisr.thedreamtable.screen.ui.Theme.*;

public class DreamTableScreen extends HandledScreen<DreamTableScreenHandler> implements ScreenActions {
	private final Viewport view = new Viewport();
	private final ControlRegistry controls = new ControlRegistry();
	private final Map<PrimaryTab, UiControl> tabControls = new EnumMap<>(PrimaryTab.class);
	private final Map<ControlId, UiControl> pageControls = new LinkedHashMap<>();
	private List<ControlSpec> chromeControls = List.of();
	private int controlsPage = -1;
	private ControlId pendingFocusRestore;
	private static final ControlId SEARCH_CONTROL = new ControlId("toolbar.search");
	private Painter painter;
	private ScreenGeometry geometry;
	private SearchBox search;
	private String searchText = "";
	private TablePage selection;
	private TablePage work;
	private TablePage requirements;
	private TablePage schemes;
	private TablePage options;
	private TablePage speed;
	private TablePage category;
	private TablePage loaded;
	private TablePage farm;
	private TablePage invites;
	private TablePage alerts;
	private TablePage materials;
	private TablePage swap;
	private PageContext ui;

	public DreamTableScreen(DreamTableScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = PANEL_WIDTH;
		this.backgroundHeight = HEADER + Viewport.DESIGN_VIEW;
		this.playerInventoryTitleY = 10_000;
		Path worldRoot = this.client != null && this.client.getServer() != null ? this.client.getServer().getSavePath(WorldSavePath.ROOT) : null;
		if (worldRoot != null) {
			FarmTemplates.reloadImports(worldRoot);
		}
	}

	@Override
	protected void init() {
		pendingFocusRestore = controls.focusedId().orElse(null);
		super.init();
		tabControls.clear();
		pageControls.clear();
		painter = new Painter(this.textRenderer);
		search = new SearchBox(this.textRenderer, searchText, () -> {
			searchText = search.text();
			view.resetScroll();
		});
		addSelectableChild(search.widget());
		for (PrimaryTab tab : PrimaryTab.values()) {
			ControlSpec placeholder = tabSpec(tab, new UiRect(0, 0, 1, 1));
			UiControl control = new UiControl(placeholder, () -> openTab(tab));
			tabControls.put(tab, control);
			addDrawableChild(control);
		}
		ui = new PageContext(this.handler, view, painter, this);
		selection = new SelectionPage(ui);
		work = new WorkPage(ui);
		requirements = new RequirementsPage(ui);
		schemes = new SchemesPage(ui);
		options = new OptionsPage(ui);
		speed = new SpeedPage(ui);
		category = new CategoryPage(ui);
		loaded = new LoadedFarmsPage(ui, search);
		farm = new FarmPage(ui, search);
		invites = new InvitesPage(ui);
		alerts = new AlertsPage(ui);
		LoadedFarmsView.invalidate();
		materials = new MaterialsPage(ui);
		swap = new SwapPage(ui, search);
		layout();
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		layout();
		updateHologram();
		super.render(context, mouseX, mouseY, delta);
		view.release(context);
		drawMouseoverTooltip(context, mouseX, mouseY);
		List<Text> tooltip = ui.takeTooltip();
		if (tooltip != null && view.inContent(mouseX, mouseY)) {
			context.drawTooltip(this.textRenderer, tooltip, mouseX, mouseY);
		}
		if (geometry.mode() == LayoutMode.COMPACT) {
			for (PrimaryTab tab : PrimaryTab.values()) {
				UiControl control = tabControls.get(tab);
				if (control.isMouseOver(mouseX, mouseY)) {
					context.drawTooltip(this.textRenderer, tabLabel(tab), mouseX, mouseY);
					break;
				}
			}
		}
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		TablePage page = page();
		painter.panel(context, geometry.panel().x(), geometry.panel().y(), geometry.panel().width(), geometry.panel().height(),
				Text.translatable("gui.the_dream_table.title"), page.subtitle());
		drawTabs(context, mouseX, mouseY);
		view.drawScrollbar(context, painter, mouseX, mouseY);
		search.draw(context, painter, searchX(), geometry.toolbar().y() + 4, searchWidth());
		view.clip(context);
		ui.beginControls(this.handler.getPage(), page);
		page.draw(context, mouseX, mouseY);
		syncPageControls();
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		view.release(context);
	}

	@Override
	protected void drawSlot(DrawContext context, Slot slot) {
		UiRect bounds = new UiRect(this.x + slot.x, this.y + slot.y, 16, 16);
		if (view.fullyVisible(bounds)) {
			super.drawSlot(context, slot);
		}
	}

	@Override
	protected boolean isPointWithinBounds(int x, int y, int width, int height, double pointX, double pointY) {
		return view.fullyVisible(new UiRect(this.x + x, this.y + y, width, height))
				&& super.isPointWithinBounds(x, y, width, height, pointX, pointY);
	}

	@Override
	protected boolean isClickOutsideBounds(double mouseX, double mouseY, int left, int top, int button) {
		return view.outsidePanel(mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		layout();
		if (button == 0 && view.beginDrag(mouseX, mouseY)) {
			return true;
		}
		if (button == 0 && this.client != null && this.client.interactionManager != null) {
			focusPageControlAt(mouseX, mouseY);
			if (page().click(mouseX, mouseY)) {
				return true;
			}
		}
		boolean handled = super.mouseClicked(mouseX, mouseY, button);
		if (handled && getFocused() instanceof UiControl control) {
			controls.focus(control.id());
		} else if (handled && getFocused() == search.widget()) {
			controls.focus(SEARCH_CONTROL);
		}
		return handled;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		layout();
		if (button == 0 && (view.drag(mouseY) || page().drag(mouseX, mouseY))) {
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		layout();
		if (button == 0) {
			view.endDrag();
			if (page().release(mouseX, mouseY)) {
				return true;
			}
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		layout();
		if (view.scrollBy(verticalAmount)) {
			layout();
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (search.focused() && keyCode != GLFW.GLFW_KEY_ESCAPE && keyCode != GLFW.GLFW_KEY_TAB) {
			search.widget().keyPressed(keyCode, scanCode, modifiers);
			return true;
		}
		if (DreamTableKeys.ROTATE.matchesKey(keyCode, scanCode)) {
			DreamTableHologram.rotate();
			return true;
		}
		if (DreamTableKeys.MIRROR.matchesKey(keyCode, scanCode)) {
			DreamTableHologram.mirror();
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_TAB) {
			controls.moveTab((modifiers & GLFW.GLFW_MOD_SHIFT) != 0).ifPresent(this::focusControl);
			revealFocused();
			return true;
		}
		if (getFocused() instanceof UiControl) {
			if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_RIGHT) {
				controls.moveHorizontal(keyCode == GLFW.GLFW_KEY_RIGHT ? 1 : -1).ifPresent(this::focusControl);
				return true;
			}
			if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN) {
				controls.moveVertical(keyCode == GLFW.GLFW_KEY_DOWN ? 1 : -1).ifPresent(this::focusControl);
				revealFocused();
				return true;
			}
		}
		if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
			return goBackOrClose();
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public void press(int buttonId) {
		this.client.interactionManager.clickButton(this.handler.syncId, buttonId);
		click();
	}

	@Override
	public void click() {
		if (this.client != null) {
			this.client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
		}
	}

	@Override
	public void message(Text text) {
		if (this.client != null && this.client.player != null) {
			this.client.inGameHud.setOverlayMessage(text, false);
		}
	}

	@Override
	public void close() {
		if (this.client != null) {
			this.client.setScreen(null);
		}
	}

	@Override
	public boolean creative() {
		return this.client != null && this.client.player != null && this.client.player.isCreative();
	}

	private TablePage page() {
		if (CategoryPage.isCategoryPage(this.handler.getPage())) {
			return category;
		}
		return switch (this.handler.getPage()) {
			case DreamTableScreenHandler.PAGE_WORK -> work;
			case DreamTableScreenHandler.PAGE_REQUIREMENTS -> requirements;
			case DreamTableScreenHandler.PAGE_SCHEMES -> schemes;
			case DreamTableScreenHandler.PAGE_OPTIONS -> options;
			case DreamTableScreenHandler.PAGE_SPEED -> speed;
			case DreamTableScreenHandler.PAGE_LOADED -> loaded;
			case DreamTableScreenHandler.PAGE_FARM -> farm;
			case DreamTableScreenHandler.PAGE_INVITES -> invites;
			case DreamTableScreenHandler.PAGE_ALERTS -> alerts;
			case DreamTableScreenHandler.PAGE_MATERIALS -> materials;
			case DreamTableScreenHandler.PAGE_SWAP -> swap;
			default -> selection;
		};
	}

	private void layout() {
		int current = this.handler.getPage();
		boolean searches = current == DreamTableScreenHandler.PAGE_SWAP || current == DreamTableScreenHandler.PAGE_LOADED
				|| current == DreamTableScreenHandler.PAGE_FARM;
		search.setHint(Text.translatable(current == DreamTableScreenHandler.PAGE_FARM
				? "gui.the_dream_table.farm_input" : "gui.the_dream_table.search"));
		if (search.sync(current, searches, (from, to) -> false)
				&& pendingFocusRestore == null) {
			setFocused(search.widget());
		} else if (!search.visible() && getFocused() == search.widget()) {
			setFocused(null);
		}
		int toolbarHeight = search.visible() ? SEARCH_SPACE : 0;
		geometry = ScreenGeometry.calculate(this.width, this.height, toolbarHeight, current == DreamTableScreenHandler.PAGE_REQUIREMENTS);
		view.layout(geometry, current, () -> page().contentHeight());
		this.backgroundWidth = geometry.panel().width();
		this.backgroundHeight = geometry.panel().height();
		this.x = view.slotOriginX();
		this.y = view.slotOriginY();
		layoutTabs();
	}

	private void layoutTabs() {
		List<ControlSpec> specs = new ArrayList<>();
		int width = geometry.tabs().width() / PrimaryTab.values().length;
		int remainder = geometry.tabs().width() % PrimaryTab.values().length;
		int x = geometry.tabs().x();
		for (PrimaryTab tab : PrimaryTab.values()) {
			int tabWidth = width + (tab.ordinal() < remainder ? 1 : 0);
			UiRect bounds = new UiRect(x, geometry.tabs().y(), tabWidth, geometry.tabs().height());
			ControlSpec spec = tabSpec(tab, bounds);
			tabControls.get(tab).update(spec, () -> openTab(tab));
			specs.add(spec);
			x += tabWidth;
		}
		if (search.visible()) {
			UiRect bounds = new UiRect(searchX(), geometry.toolbar().y() + 4, searchWidth(), 18);
			specs.add(new ControlSpec(SEARCH_CONTROL, ControlGroup.CONTENT, bounds,
					Text.translatable("gui.the_dream_table.search"), Text.empty(), true, false, Text.empty(), -1, 0));
		}
		chromeControls = List.copyOf(specs);
	}

	private int searchX() {
		return collectionSearch() ? view.collectionX() : view.flowX();
	}

	private int searchWidth() {
		return collectionSearch() ? view.collectionWidth() : CARD_WIDTH;
	}

	private boolean collectionSearch() {
		int current = this.handler.getPage();
		return current == DreamTableScreenHandler.PAGE_SWAP || current == DreamTableScreenHandler.PAGE_LOADED;
	}

	private ControlSpec tabSpec(PrimaryTab tab, UiRect bounds) {
		boolean selected = geometry != null && PageRoutes.tabFor(this.handler.getPage()) == tab;
		return new ControlSpec(new ControlId("tab." + tab.name().toLowerCase(Locale.ROOT)), ControlGroup.PRIMARY_TABS, bounds,
				tabLabel(tab), Text.empty(),
				true, selected, Text.empty(), 0, tab.ordinal());
	}

	private void drawTabs(DrawContext context, int mouseX, int mouseY) {
		for (PrimaryTab tab : PrimaryTab.values()) {
			UiControl control = tabControls.get(tab);
			ControlSpec spec = chromeControls.get(tab.ordinal());
			painter.tab(context, tabIcon(tab), tabLabel(tab), spec.bounds(), control.isMouseOver(mouseX, mouseY) || control.isFocused(),
					spec.selected(), geometry.mode() == LayoutMode.COMPACT);
		}
	}

	private void openTab(PrimaryTab tab) {
		int page = PageRoutes.rootFor(tab, !handler.getSelectedFarm().isEmpty());
		int button = switch (tab) {
			case BUILD -> DreamTableScreenHandler.BUTTON_TAB_BUILD;
			case FARMS -> DreamTableScreenHandler.BUTTON_TAB_FARMS;
			case SCHEMATICS -> DreamTableScreenHandler.BUTTON_TAB_SCHEMATICS;
			case SETTINGS -> DreamTableScreenHandler.BUTTON_TAB_SETTINGS;
		};
		handler.preview(handler.getSelectedFarm(), page);
		press(button);
	}

	private boolean goBackOrClose() {
		int page = handler.getPage();
		int parent = PageRoutes.parentOf(page);
		if (parent == PageRoutes.NO_PARENT) {
			close();
			return true;
		}
		int button = CategoryPage.isCategoryPage(page) ? DreamTableScreenHandler.BUTTON_CATEGORY_BACK : switch (page) {
			case DreamTableScreenHandler.PAGE_WORK -> DreamTableScreenHandler.BUTTON_MENU;
			case DreamTableScreenHandler.PAGE_REQUIREMENTS -> DreamTableScreenHandler.BUTTON_BACK;
			case DreamTableScreenHandler.PAGE_MATERIALS -> DreamTableScreenHandler.BUTTON_MATERIALS_BACK;
			case DreamTableScreenHandler.PAGE_SWAP -> DreamTableScreenHandler.BUTTON_SWAP_BACK;
			case DreamTableScreenHandler.PAGE_SPEED -> DreamTableScreenHandler.BUTTON_SPEED_BACK;
			case DreamTableScreenHandler.PAGE_FARM -> DreamTableScreenHandler.BUTTON_FARM_BACK;
			case DreamTableScreenHandler.PAGE_INVITES -> DreamTableScreenHandler.BUTTON_LOADED_FARMS;
			case DreamTableScreenHandler.PAGE_ALERTS -> DreamTableScreenHandler.BUTTON_FARM_OPEN;
			default -> throw new IllegalStateException("No back button for page " + page);
		};
		handler.preview(handler.getSelectedFarm(), parent);
		press(button);
		return true;
	}

	private void focusControl(ControlId id) {
		if (SEARCH_CONTROL.equals(id)) {
			setFocused(search.widget());
			return;
		}
		for (UiControl control : tabControls.values()) {
			if (control.id().equals(id)) {
				setFocused(control);
				break;
			}
		}
		UiControl control = pageControls.get(id);
		if (control != null) {
			setFocused(control);
		}
	}

	private void focusPageControlAt(double mouseX, double mouseY) {
		for (ControlSpec spec : ui.controls()) {
			if (spec.bounds().contains(mouseX, mouseY)) {
				controls.focus(spec.id());
				focusControl(spec.id());
				return;
			}
		}
	}

	private void syncPageControls() {
		List<ControlSpec> pageSpecs = ui.controls();
		Map<ControlId, ControlSpec> desired = new LinkedHashMap<>();
		for (ControlSpec spec : pageSpecs) {
			desired.put(spec.id(), spec);
		}
		for (ControlId id : List.copyOf(pageControls.keySet())) {
			if (!desired.containsKey(id)) {
				UiControl removed = pageControls.remove(id);
				remove(removed);
			}
		}
		for (ControlSpec spec : pageSpecs) {
			UiControl control = pageControls.get(spec.id());
			if (control == null) {
				control = new UiControl(spec, ui.action(spec.id()));
				pageControls.put(spec.id(), control);
				addDrawableChild(control);
			} else {
				control.update(spec, ui.action(spec.id()));
			}
			control.setMouseInteractive(view.visible(spec.bounds()));
		}

		List<ControlSpec> all = new ArrayList<>(chromeControls);
		all.addAll(pageSpecs);
		int page = handler.getPage();
		boolean changed = controlsPage != page;
		controls.reconcile(all);
		controlsPage = page;
		if (changed) {
			ControlSpec first = search.visible()
					? chromeControls.stream().filter(spec -> spec.id().equals(SEARCH_CONTROL)).findFirst().orElseThrow()
					: pageSpecs.stream().filter(ControlSpec::enabled).findFirst().orElseGet(() -> chromeControls.getFirst());
			controls.focus(first.id());
			focusControl(first.id());
			revealFocused();
		} else if (getFocused() == null) {
			controls.focusedId().ifPresent(this::focusControl);
		}
		pendingFocusRestore = null;
	}

	private void revealFocused() {
		if (controls.focusedSpec().map(ControlSpec::group).orElse(ControlGroup.PRIMARY_TABS) != ControlGroup.CONTENT
				|| controls.focusedId().filter(SEARCH_CONTROL::equals).isPresent()) {
			controls.takeRevealRequest();
			return;
		}
		controls.takeRevealRequest().ifPresent(bounds -> {
			if (view.reveal(bounds)) {
				this.y = view.slotOriginY();
			}
		});
	}

	private static Text tabLabel(PrimaryTab tab) {
		return Text.translatable("gui.the_dream_table.tab." + tab.name().toLowerCase(Locale.ROOT));
	}

	private static ItemStack tabIcon(PrimaryTab tab) {
		return new ItemStack(switch (tab) {
			case BUILD -> Items.CRAFTING_TABLE;
			case FARMS -> Items.LODESTONE;
			case SCHEMATICS -> Items.BOOK;
			case SETTINGS -> Items.REPEATER;
		});
	}

	private void updateHologram() {
		if (this.handler.getSelectedFarm().isEmpty()) {
			DreamTableHologram.clear();
			return;
		}
		DreamTableHologram.show(this.handler.getTablePos(), this.handler.getSelectedFarm(), this.handler.getBuildIndex(), this.handler.getPhase() == ProjectState.BLOCKED);
	}
}
