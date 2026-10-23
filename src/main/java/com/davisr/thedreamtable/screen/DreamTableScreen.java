package com.davisr.thedreamtable.screen;

import com.davisr.thedreamtable.client.DreamTableHologram;
import com.davisr.thedreamtable.client.DreamTableKeys;
import com.davisr.thedreamtable.farm.FarmTemplates;
import com.davisr.thedreamtable.screen.page.MaterialsPage;
import com.davisr.thedreamtable.screen.page.CategoryPage;
import com.davisr.thedreamtable.screen.page.GroupPage;
import com.davisr.thedreamtable.screen.page.LoadedFarmsPage;
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
import com.davisr.thedreamtable.screen.ui.Painter;
import com.davisr.thedreamtable.screen.ui.SearchBox;
import com.davisr.thedreamtable.screen.ui.Viewport;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;

import static com.davisr.thedreamtable.screen.ui.Theme.*;

public class DreamTableScreen extends HandledScreen<DreamTableScreenHandler> implements ScreenActions {
	private final Viewport view = new Viewport();
	private Painter painter;
	private SearchBox search;
	private String searchText = "";
	private TablePage selection;
	private TablePage work;
	private TablePage requirements;
	private TablePage schemes;
	private TablePage options;
	private TablePage speed;
	private TablePage category;
	private TablePage group;
	private TablePage loaded;
	private TablePage materials;
	private TablePage swap;

	public DreamTableScreen(DreamTableScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = PANEL_WIDTH;
		this.backgroundHeight = HEADER + Viewport.DESIGN_VIEW;
		this.playerInventoryTitleY = 10_000;
		Path worldRoot = this.client != null && this.client.getServer() != null ? this.client.getServer().getSavePath(WorldSavePath.ROOT) : null;
		FarmTemplates.reloadImports(worldRoot);
	}

	@Override
	protected void init() {
		super.init();
		painter = new Painter(this.textRenderer);
		search = new SearchBox(this.textRenderer, searchText, () -> {
			searchText = search.text();
			view.resetScroll();
		});
		addSelectableChild(search.widget());
		PageContext ui = new PageContext(this.handler, view, painter, this);
		selection = new SelectionPage(ui);
		work = new WorkPage(ui);
		requirements = new RequirementsPage(ui);
		schemes = new SchemesPage(ui);
		options = new OptionsPage(ui);
		speed = new SpeedPage(ui);
		category = new CategoryPage(ui);
		group = new GroupPage(ui);
		loaded = new LoadedFarmsPage(ui);
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
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		TablePage page = page();
		painter.panel(context, view.originX(), view.originY(), view.visibleHeight(), Text.translatable("gui.the_dream_table.title"), page.subtitle());
		view.drawScrollbar(context, painter, mouseX, mouseY);
		search.draw(context, painter, view.cardX(), view.originY() + HEADER + 5);
		view.clip(context);
		page.draw(context, mouseX, mouseY);
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		view.release(context);
	}

	@Override
	protected void drawSlot(DrawContext context, Slot slot) {
		int top = this.y + slot.y;
		if (top + 16 > view.contentTop() && top < view.contentBottom()) {
			super.drawSlot(context, slot);
		}
	}

	@Override
	protected boolean isPointWithinBounds(int x, int y, int width, int height, double pointX, double pointY) {
		return view.inContent(pointX, pointY) && super.isPointWithinBounds(x, y, width, height, pointX, pointY);
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
		if (button == 0 && this.client != null && this.client.interactionManager != null && page().click(mouseX, mouseY)) {
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
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
		if (search.focused() && keyCode != GLFW.GLFW_KEY_ESCAPE) {
			search.widget().keyPressed(keyCode, scanCode, modifiers);
			return true;
		}
		if (DreamTableKeys.ROTATE.matchesKey(keyCode, scanCode)) {
			DreamTableHologram.rotate();
			return true;
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
		if (GroupPage.isGroupPage(this.handler.getPage())) {
			return group;
		}
		return switch (this.handler.getPage()) {
			case DreamTableScreenHandler.PAGE_WORK -> work;
			case DreamTableScreenHandler.PAGE_REQUIREMENTS -> requirements;
			case DreamTableScreenHandler.PAGE_SCHEMES -> schemes;
			case DreamTableScreenHandler.PAGE_OPTIONS -> options;
			case DreamTableScreenHandler.PAGE_SPEED -> speed;
			case DreamTableScreenHandler.PAGE_LOADED -> loaded;
			case DreamTableScreenHandler.PAGE_MATERIALS -> materials;
			case DreamTableScreenHandler.PAGE_SWAP -> swap;
			default -> selection;
		};
	}

	private void layout() {
		int current = this.handler.getPage();
		if (search.sync(current, DreamTableScreenHandler.PAGE_SWAP)) {
			setFocused(search.widget());
		} else if (!search.visible() && getFocused() == search.widget()) {
			setFocused(null);
		}
		int searchSpace = search.visible() ? SEARCH_SPACE : 0;
		view.layout(this.width, this.height, current, searchSpace, page().contentHeight());
		this.backgroundWidth = PANEL_WIDTH;
		this.backgroundHeight = view.visibleHeight();
		this.x = view.originX();
		this.y = view.panelY();
	}

	private void updateHologram() {
		if (this.handler.getSelectedFarm().isEmpty()) {
			DreamTableHologram.clear();
			return;
		}
		DreamTableHologram.show(this.handler.getTablePos(), this.handler.getSelectedFarm(), this.handler.getBuildIndex(), this.handler.getPhase() == ProjectState.BLOCKED);
	}
}
