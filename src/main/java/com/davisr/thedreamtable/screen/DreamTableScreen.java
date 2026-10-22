package com.davisr.thedreamtable.screen;

import com.davisr.thedreamtable.client.DreamTableHologram;
import com.davisr.thedreamtable.client.DreamTableKeys;
import com.davisr.thedreamtable.network.PlaceFarmPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import com.davisr.thedreamtable.farm.FarmOptions;
import com.davisr.thedreamtable.farm.FarmOptions.FarmOption;
import com.davisr.thedreamtable.farm.FarmTemplate;
import com.davisr.thedreamtable.farm.FarmTemplates;
import com.davisr.thedreamtable.farm.MaterialSpec;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.List;

public class DreamTableScreen extends HandledScreen<DreamTableScreenHandler> {
	private static final int PANEL_WIDTH = DreamTableLayout.PANEL_WIDTH;
	private static final int HEADER = DreamTableLayout.HEADER;
	private static final int PAD = 10;
	private static final int CARD_HEIGHT = 32;
	private static final int CARD_GAP = 5;
	private static final int BUTTON_HEIGHT = DreamTableLayout.BUTTON;
	private static final int CARD_X = 10;
	private static final int CARD_WIDTH = 168;
	private static final int TRACK_WIDTH = 8;

	private static final int HEADER_COLOR = 0xFF2B6DA7;
	private static final int PANEL = 0xFFF4ECDF;
	private static final int CARD_FILL = 0xFFF5EFE4;
	private static final int CARD_FILL_HOVER = 0xFFFBF8F1;
	private static final int CARD_FILL_SELECTED = 0xFFEEE7DA;
	private static final int CARD_SHADE = 0xFFDDD3C0;
	private static final int NAVY = 0xFF06274F;
	private static final int BLUE = 0xFF2C6CA9;
	private static final int ACCENT = 0xFF2B6DA7;
	private static final int TITLE = 0xFFFFFFFF;
	private static final int SUBTITLE = 0xFFC9DDEE;
	private static final int NAME = 0xFF14213D;
	private static final int HINT = 0xFF5F6E7E;
	private static final int MUTED = 0xFF8A94A0;
	private static final int TRACK_EDGE = 0xFF04203F;
	private static final int TRACK_FILL = 0xFF0B3560;
	private static final int THUMB_EDGE = 0xFF0A2A52;
	private static final int THUMB_FILL = 0xFF2F78C4;
	private static final int THUMB_LIGHT = 0xFF5AAEF0;
	private static final int THUMB_DARK = 0xFF1E5A9A;

	private int scroll;
	private int scrolledPage = -1;
	private boolean draggingScroll;
	private int dragGrab;
	private int originX;
	private int originY;
	private boolean clipContent;

	public DreamTableScreen(DreamTableScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = PANEL_WIDTH;
		this.backgroundHeight = HEADER + designView();
		this.playerInventoryTitleY = 10_000;
		java.nio.file.Path worldRoot = this.client != null && this.client.getServer() != null
				? this.client.getServer().getSavePath(net.minecraft.util.WorldSavePath.ROOT)
				: null;
		FarmTemplates.reloadImports(worldRoot);
	}

	@Override
	protected void init() {
		super.init();
		applyScroll();
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		applyScroll();
		updateHologram();
		super.render(context, mouseX, mouseY, delta);
		releaseClip(context);
		drawMouseoverTooltip(context, mouseX, mouseY);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		applyScroll();
		int max = maxScroll();
		if (max <= 0) {
			return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
		}
		scroll = (int) Math.max(0, Math.min(max, scroll - verticalAmount * (CARD_HEIGHT + CARD_GAP)));
		applyScroll();
		return true;
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		int x = originX;
		int y = originY;
		int height = visibleHeight();
		int w = PANEL_WIDTH;
		context.fill(x + 3, y + 4, x + w + 3, y + height + 4, 0x30000000);

		fillTop(context, x - 1, y - 1, x + w + 1, y + HEADER, 0xFF0A2A4E);
		fillTop(context, x, y, x + w, y + HEADER - 1, HEADER_COLOR);
		context.fill(x + 2, y, x + w - 2, y + 1, 0xFF7FEBFD);
		context.fill(x + 1, y + 1, x + w - 1, y + 2, 0xFF3FA6E0);
		context.fill(x, y + 2, x + 1, y + HEADER - 4, 0xFF4FB8F0);
		context.fill(x + 1, y + 2, x + 2, y + HEADER - 4, 0xFF3A8ED0);
		context.fill(x + w - 2, y + 2, x + w - 1, y + HEADER - 4, 0xFF2160A0);
		context.fill(x + w - 1, y + 2, x + w, y + HEADER - 1, 0xFF1D4F85);
		context.fill(x + 2, y + HEADER - 5, x + w - 2, y + HEADER - 4, 0xFF3C88C8);
		context.fill(x, y + HEADER - 4, x + w, y + HEADER - 1, 0xFF1B4E86);
		context.fill(x, y + HEADER - 1, x + w, y + HEADER, 0xFF0A2A4E);

		context.fill(x, y + HEADER, x + w, y + height, PANEL);
		context.fill(x, y + HEADER, x + 1, y + height, 0xFFFFFAF0);
		context.fill(x + w - 2, y + HEADER, x + w - 1, y + height, 0xFFE6DCC9);
		context.fill(x + w - 1, y + HEADER, x + w, y + height, 0xFFC8BBA3);
		context.fill(x, y + height - 2, x + w, y + height - 1, 0xFFC9BDA8);
		context.fill(x, y + height - 1, x + w, y + height, 0xFF948877);
		context.drawBorder(x + 3, y + HEADER + 3, w - 6, height - HEADER - 7, 0xFFE3D8C6);

		drawMark(context, x + 12, y + 14);
		int textLimit = w - 46;
		context.drawText(this.textRenderer, trim(Text.translatable("gui.the_dream_table.title"), textLimit), x + 34, y + 10, TITLE, false);
		context.drawText(this.textRenderer, trim(subtitle(), textLimit), x + 34, y + 24, SUBTITLE, false);
		drawScrollbar(context, mouseX, mouseY);
		clipToContent(context);

		switch (this.handler.getPage()) {
			case DreamTableScreenHandler.PAGE_WORK -> drawWork(context, mouseX, mouseY);
			case DreamTableScreenHandler.PAGE_REQUIREMENTS -> drawRequirements(context, mouseX, mouseY);
			case DreamTableScreenHandler.PAGE_SCHEMES -> drawSchemes(context, mouseX, mouseY);
			case DreamTableScreenHandler.PAGE_OPTIONS -> drawOptions(context, mouseX, mouseY);
			default -> drawSelection(context, mouseX, mouseY);
		}
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		releaseClip(context);
	}

	@Override
	protected void drawSlot(DrawContext context, Slot slot) {
		int top = this.y + slot.y;
		if (top + 16 <= contentTop() || top >= contentBottom()) {
			return;
		}
		super.drawSlot(context, slot);
	}

	@Override
	protected boolean isPointWithinBounds(int x, int y, int width, int height, double pointX, double pointY) {
		if (pointX < contentLeft() || pointX >= contentRight() || pointY < contentTop() || pointY >= contentBottom()) {
			return false;
		}
		return super.isPointWithinBounds(x, y, width, height, pointX, pointY);
	}

	@Override
	protected boolean isClickOutsideBounds(double mouseX, double mouseY, int left, int top, int button) {
		int height = visibleHeight();
		return mouseX < originX || mouseY < originY || mouseX >= originX + PANEL_WIDTH || mouseY >= originY + height;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		applyScroll();
		if (button == 0 && maxScroll() > 0 && inside(mouseX, mouseY, scrollTrackX() - 3, scrollTrackY(), TRACK_WIDTH + 6, scrollTrackHeight())) {
			draggingScroll = true;
			int thumbTop = thumbY();
			dragGrab = mouseY >= thumbTop && mouseY <= thumbTop + thumbHeight() ? (int) mouseY - thumbTop : thumbHeight() / 2;
			dragScroll(mouseY);
			return true;
		}
		if (button == 0 && this.client != null && this.client.interactionManager != null) {
			switch (this.handler.getPage()) {
				case DreamTableScreenHandler.PAGE_WORK -> {
					if (clickWork(mouseX, mouseY)) {
						return true;
					}
				}
				case DreamTableScreenHandler.PAGE_REQUIREMENTS -> {
					if (hit(mouseX, mouseY, cardX(), backButtonY(), cardWidth(), BUTTON_HEIGHT)) {
						this.handler.preview(this.handler.getSelectedFarm(), DreamTableScreenHandler.PAGE_WORK);
						press(DreamTableScreenHandler.BUTTON_BACK);
						return true;
					}
				}
				case DreamTableScreenHandler.PAGE_SCHEMES -> {
					if (clickSchemes(mouseX, mouseY)) {
						return true;
					}
				}
				case DreamTableScreenHandler.PAGE_OPTIONS -> {
					if (clickOptions(mouseX, mouseY)) {
						return true;
					}
				}
				default -> {
					for (int index = 0; index < FarmOptions.ALL.size(); index++) {
						if (hit(mouseX, mouseY, cardX(), cardY(index), cardWidth(), CARD_HEIGHT)) {
							FarmOption option = FarmOptions.ALL.get(index);
							this.handler.preview(option.id(), DreamTableScreenHandler.PAGE_WORK);
							press(index);
							return true;
						}
					}
					if (hit(mouseX, mouseY, cardX(), cardY(FarmOptions.ALL.size()), cardWidth(), CARD_HEIGHT)) {
						this.handler.preview(this.handler.getSelectedFarm(), DreamTableScreenHandler.PAGE_SCHEMES);
						press(DreamTableScreenHandler.BUTTON_SCHEMES);
						return true;
					}
					if (hit(mouseX, mouseY, cardX(), cardY(FarmOptions.ALL.size() + 1), cardWidth(), CARD_HEIGHT)) {
						this.handler.preview(this.handler.getSelectedFarm(), DreamTableScreenHandler.PAGE_OPTIONS);
						press(DreamTableScreenHandler.BUTTON_OPTIONS);
						return true;
					}
				}
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		applyScroll();
		if (draggingScroll && button == 0) {
			dragScroll(mouseY);
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		applyScroll();
		if (button == 0) {
			draggingScroll = false;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	private void drawSelection(DrawContext context, int mouseX, int mouseY) {
		for (int index = 0; index < FarmOptions.ALL.size(); index++) {
			FarmOption option = FarmOptions.ALL.get(index);
			boolean selected = option.id().equals(this.handler.getSelectedFarm());
			drawOption(context, option.icon().getDefaultStack(), option.title(), cardX(), cardY(index), mouseX, mouseY, selected, true);
		}
		drawOption(context, new ItemStack(net.minecraft.item.Items.BOOK), Text.translatable("gui.the_dream_table.schemes"), cardX(), cardY(FarmOptions.ALL.size()), mouseX, mouseY, false, true);
		drawOption(context, new ItemStack(net.minecraft.item.Items.REPEATER), Text.translatable("gui.the_dream_table.options"), cardX(), cardY(FarmOptions.ALL.size() + 1), mouseX, mouseY, false, true);
	}

	private void drawOptions(DrawContext context, int mouseX, int mouseY) {
		int speed = this.handler.getBlocksPerSecond();
		drawOption(context, new ItemStack(net.minecraft.item.Items.CLOCK), Text.translatable("gui.the_dream_table.speed_1"), cardX(), cardY(0), mouseX, mouseY, speed == 1, true);
		drawOption(context, new ItemStack(net.minecraft.item.Items.REPEATER), Text.translatable("gui.the_dream_table.speed_2"), cardX(), cardY(1), mouseX, mouseY, speed == 2, true);
		drawOption(context, new ItemStack(net.minecraft.item.Items.COMPARATOR), Text.translatable("gui.the_dream_table.speed_4"), cardX(), cardY(2), mouseX, mouseY, speed == 4, true);
		drawOption(context, new ItemStack(net.minecraft.item.Items.REDSTONE_BLOCK), Text.translatable("gui.the_dream_table.speed_8"), cardX(), cardY(3), mouseX, mouseY, speed == 8, true);
		drawAction(context, Text.translatable("gui.the_dream_table.back"), cardX(), optionsBackY(), mouseX, mouseY, true, false);
	}

	private boolean clickOptions(double mouseX, double mouseY) {
		int[] speeds = {1, 2, 4, 8};
		for (int index = 0; index < speeds.length; index++) {
			if (hit(mouseX, mouseY, cardX(), cardY(index), cardWidth(), CARD_HEIGHT)) {
				this.handler.previewSpeed(speeds[index]);
				press(DreamTableScreenHandler.BUTTON_SPEED + speeds[index]);
				return true;
			}
		}
		if (hit(mouseX, mouseY, cardX(), optionsBackY(), cardWidth(), BUTTON_HEIGHT)) {
			this.handler.preview(this.handler.getSelectedFarm(), DreamTableScreenHandler.PAGE_SELECT);
			press(DreamTableScreenHandler.BUTTON_OPTIONS_BACK);
			return true;
		}
		return false;
	}

	private int optionsBackY() {
		return cardY(3) + CARD_HEIGHT + 6;
	}

	private void drawSchemes(DrawContext context, int mouseX, int mouseY) {
		List<FarmOption> schemes = FarmTemplates.imported();
		if (schemes.isEmpty()) {
			Text empty = Text.translatable("gui.the_dream_table.schemes_empty");
			int textY = panelY() + HEADER + PAD + 8;
			for (var line : this.textRenderer.wrapLines(empty, cardWidth())) {
				context.drawText(this.textRenderer, line, cardX(), textY, HINT, false);
				textY += 12;
			}
		} else {
			for (int index = 0; index < schemes.size(); index++) {
				FarmOption option = schemes.get(index);
				boolean selected = option.id().equals(this.handler.getSelectedFarm());
				drawOption(context, option.icon().getDefaultStack(), option.title(), cardX(), cardY(index), mouseX, mouseY, selected, true);
			}
		}
		drawAction(context, Text.translatable("gui.the_dream_table.back"), cardX(), schemesBackY(), mouseX, mouseY, true, false);
	}

	private boolean clickSchemes(double mouseX, double mouseY) {
		List<FarmOption> schemes = FarmTemplates.imported();
		for (int index = 0; index < schemes.size(); index++) {
			if (hit(mouseX, mouseY, cardX(), cardY(index), cardWidth(), CARD_HEIGHT)) {
				this.handler.preview(schemes.get(index).id(), DreamTableScreenHandler.PAGE_WORK);
				press(DreamTableScreenHandler.BUTTON_SCHEME + index);
				return true;
			}
		}
		if (hit(mouseX, mouseY, cardX(), schemesBackY(), cardWidth(), BUTTON_HEIGHT)) {
			this.handler.preview(this.handler.getSelectedFarm(), DreamTableScreenHandler.PAGE_SELECT);
			press(DreamTableScreenHandler.BUTTON_SCHEMES_BACK);
			return true;
		}
		return false;
	}

	private void drawWork(DrawContext context, int mouseX, int mouseY) {
		FarmOption option = FarmOptions.byId(this.handler.getSelectedFarm()).orElse(null);
		ItemStack icon = option == null ? new ItemStack(net.minecraft.item.Items.STRUCTURE_BLOCK) : option.icon().getDefaultStack();
		Text name = option == null ? Text.literal(this.handler.getSelectedFarm()) : option.title();
		drawOption(context, icon, name, cardX(), cardY(0), mouseX, mouseY, true, true);
		context.drawText(this.textRenderer, trim(statusText(), cardWidth()), cardX(), workStatusY(), HINT, false);

		int barX = cardX();
		int barY = workBarY();
		context.fill(barX, barY, barX + cardWidth(), barY + 6, 0xFF163E66);
		if (this.handler.getTotal() > 0) {
			int filled = Math.max(0, cardWidth() * this.handler.getBuildIndex() / this.handler.getTotal());
			context.fill(barX, barY, barX + filled, barY + 6, 0xFF3E86C4);
		}

		int buttons = workButtonsY();
		drawAction(context, Text.translatable("gui.the_dream_table.requirements"), cardX(), buttons, mouseX, mouseY, true, false);
		boolean buildEnabled = buildEnabled();
		Text buildLabel = switch (this.handler.getPhase()) {
			case 1 -> Text.translatable("gui.the_dream_table.building_short");
			case 2 -> Text.translatable("gui.the_dream_table.retry");
			case 3 -> Text.translatable("gui.the_dream_table.done_short");
			default -> Text.translatable("gui.the_dream_table.build");
		};
		drawAction(context, buildLabel, cardX(), buttons + BUTTON_HEIGHT + 6, mouseX, mouseY, buildEnabled, true);
		drawAction(context, Text.translatable("gui.the_dream_table.discard"), cardX(), buttons + (BUTTON_HEIGHT + 6) * 2, mouseX, mouseY, true, false);
	}

	private void drawRequirements(DrawContext context, int mouseX, int mouseY) {
		FarmTemplate template = FarmTemplates.get(this.handler.getSelectedFarm());
		int rows = template == null ? 0 : template.materials().size();
		for (int index = 0; index < rows; index++) {
			MaterialSpec spec = template.materials().get(index);
			Slot slot = this.handler.getSlot(index);
			int slotX = panelX() + slot.x;
			int slotY = panelY() + slot.y;
			int have = this.handler.supplied(index);
			boolean complete = have >= spec.count();
			drawSlotFrame(context, slotX, slotY);
			context.drawItem(spec.item().getDefaultStack(), slotX, slotY);
			if (complete) {
				context.drawBorder(slotX - 1, slotY - 1, 18, 18, ACCENT);
			}
			Text count = Text.translatable("gui.the_dream_table.count", have, spec.count());
			context.drawText(this.textRenderer, count, slotX + 8 - this.textRenderer.getWidth(count) / 2, slotY + 16, complete ? ACCENT : HINT, false);
		}
		drawAction(context, Text.translatable("gui.the_dream_table.back"), cardX(), backButtonY(), mouseX, mouseY, true, false);

		Text inventory = Text.translatable("gui.the_dream_table.inventory");
		context.drawText(this.textRenderer, inventory, panelX() + 16, panelY() + DreamTableLayout.inventoryY(materialCount()) - 12, HINT, false);
		for (int index = FarmTemplate.MAX_SLOTS; index < this.handler.slots.size(); index++) {
			Slot slot = this.handler.getSlot(index);
			drawSlotFrame(context, panelX() + slot.x, panelY() + slot.y);
		}
	}

	private void drawMark(DrawContext context, int x, int y) {
		context.fill(x + 1, y + 1, x + 19, y + 19, 0xFF1F5489);
		context.fill(x, y, x + 18, y + 18, 0xFFF4FBFF);
		context.fill(x + 2, y + 2, x + 16, y + 16, 0xFF245C98);
		context.fill(x + 6, y + 2, x + 7, y + 16, 0xFF4E92D4);
		context.fill(x + 11, y + 2, x + 12, y + 16, 0xFF4E92D4);
		context.fill(x + 2, y + 6, x + 16, y + 7, 0xFF4E92D4);
		context.fill(x + 2, y + 11, x + 16, y + 12, 0xFF4E92D4);
	}

	private void fillTop(DrawContext context, int x1, int y1, int x2, int y2, int color) {
		context.fill(x1 + 2, y1, x2 - 2, y1 + 1, color);
		context.fill(x1 + 1, y1 + 1, x2 - 1, y1 + 2, color);
		context.fill(x1, y1 + 2, x2, y2, color);
	}

	private void ring(DrawContext context, int x, int y, int w, int h, int color) {
		context.fill(x + 1, y, x + w - 1, y + 1, color);
		context.fill(x + 1, y + h - 1, x + w - 1, y + h, color);
		context.fill(x, y + 1, x + 1, y + h - 1, color);
		context.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
	}

	private void drawFrame(DrawContext context, int x, int y, int w, int h, int outer, int middle, int inner, int fill, int shade, int highlight) {
		ring(context, x, y, w, h, outer);
		ring(context, x + 1, y + 1, w - 2, h - 2, middle);
		ring(context, x + 2, y + 2, w - 4, h - 4, inner);
		context.fill(x + 3, y + 3, x + w - 3, y + h - 3, fill);
		context.fill(x + 3, y + h - 4, x + w - 3, y + h - 3, shade);
		context.fill(x + w - 4, y + 3, x + w - 3, y + h - 3, shade);
		context.fill(x + 3, y + 3, x + w - 4, y + 4, highlight);
	}

	private void drawOption(DrawContext context, ItemStack icon, Text label, int x, int y, int mouseX, int mouseY, boolean selected, boolean enabled) {
		boolean hovered = enabled && hit(mouseX, mouseY, x, y, cardWidth(), CARD_HEIGHT);
		int w = cardWidth();
		if (!enabled) {
			drawFrame(context, x, y, w, CARD_HEIGHT, 0xFF7C8794, 0xFFA9B3BE, 0xFF7C8794, 0xFFE4DDCF, 0xFFD3CBBB, 0xFFEFE9DD);
		} else if (selected) {
			ring(context, x, y, w, CARD_HEIGHT, 0xFF1772B9);
			ring(context, x + 1, y + 1, w - 2, CARD_HEIGHT - 2, 0xFF38BEFB);
			ring(context, x + 2, y + 2, w - 4, CARD_HEIGHT - 4, 0xFF2A9AE0);
			context.fill(x + 3, y + 3, x + w - 3, y + CARD_HEIGHT - 3, 0xFFFFFFFF);
			context.fill(x + 4, y + 4, x + w - 4, y + CARD_HEIGHT - 4, CARD_FILL_SELECTED);
		} else {
			drawFrame(context, x, y, w, CARD_HEIGHT, NAVY, hovered ? 0xFF3F86C6 : BLUE, NAVY, hovered ? CARD_FILL_HOVER : CARD_FILL, CARD_SHADE, 0xFFFFFFFF);
		}
		drawIconBox(context, x + 7, y + 6);
		context.drawItem(icon, x + 9, y + 8);
		context.drawText(this.textRenderer, trim(label, w - 46), x + 34, y + 12, enabled ? NAME : MUTED, false);
	}

	private void drawIconBox(DrawContext context, int x, int y) {
		context.fill(x, y, x + 20, y + 20, 0xFF6F95BA);
		context.fill(x + 1, y + 1, x + 19, y + 19, 0xFFF3EEE4);
	}

	private void drawAction(DrawContext context, Text label, int x, int y, int mouseX, int mouseY, boolean enabled, boolean primary) {
		boolean hovered = enabled && hit(mouseX, mouseY, x, y, cardWidth(), BUTTON_HEIGHT);
		int w = cardWidth();
		int color;
		if (!enabled) {
			drawFrame(context, x, y, w, BUTTON_HEIGHT, 0xFF7C8794, 0xFFA9B3BE, 0xFF7C8794, 0xFFE4DDCF, 0xFFD3CBBB, 0xFFEFE9DD);
			color = MUTED;
		} else if (primary) {
			drawFrame(context, x, y, w, BUTTON_HEIGHT, NAVY, hovered ? 0xFF5BB8F0 : 0xFF3FA6E0, NAVY, hovered ? 0xFF3B82BE : ACCENT, 0xFF1F5E98, 0xFF63A8E0);
			color = TITLE;
		} else {
			drawFrame(context, x, y, w, BUTTON_HEIGHT, NAVY, hovered ? 0xFF3F86C6 : BLUE, NAVY, hovered ? CARD_FILL_HOVER : CARD_FILL, CARD_SHADE, 0xFFFFFFFF);
			color = NAME;
		}
		String shown = trim(label, w - 16);
		context.drawText(this.textRenderer, shown, x + (w - this.textRenderer.getWidth(shown)) / 2, y + 10, color, false);
	}

	private void drawSlotFrame(DrawContext context, int x, int y) {
		context.fill(x - 2, y - 2, x + 18, y + 18, 0xFF6F95BA);
		context.fill(x - 1, y - 1, x + 17, y + 17, 0xFFF3EEE4);
	}

	private int workStatusY() {
		return cardY(0) + CARD_HEIGHT + 8;
	}

	private int workBarY() {
		return workStatusY() + 14;
	}

	private int workButtonsY() {
		return workBarY() + 16;
	}

	private boolean clickWork(double mouseX, double mouseY) {
		int buttons = workButtonsY();
		if (hit(mouseX, mouseY, cardX(), buttons, cardWidth(), BUTTON_HEIGHT)) {
			com.davisr.thedreamtable.client.DreamTableClipboard.pin(this.handler.getTablePos());
			press(DreamTableScreenHandler.BUTTON_REQUIREMENTS);
			if (this.client != null) {
				this.client.setScreen(null);
			}
			return true;
		}
		if (hit(mouseX, mouseY, cardX(), buttons + BUTTON_HEIGHT + 6, cardWidth(), BUTTON_HEIGHT)) {
			if (buildEnabled()) {
				if (this.handler.getPhase() == 0 && !DreamTableHologram.hasPlacement()) {
					if (this.client != null && this.client.player != null) {
						this.client.inGameHud.setOverlayMessage(Text.translatable("gui.the_dream_table.place_first"), false);
					}
					return true;
				}
				ClientPlayNetworking.send(new PlaceFarmPayload(this.handler.getTablePos(), DreamTableHologram.anchorForBuild(), DreamTableHologram.previewFacing()));
				if (this.client != null) {
					this.client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
				}
			}
			return true;
		}
		if (hit(mouseX, mouseY, cardX(), buttons + (BUTTON_HEIGHT + 6) * 2, cardWidth(), BUTTON_HEIGHT)) {
			this.handler.preview("", DreamTableScreenHandler.PAGE_SELECT);
			DreamTableHologram.clear();
			com.davisr.thedreamtable.client.DreamTableClipboard.close();
			press(DreamTableScreenHandler.BUTTON_DISCARD);
			return true;
		}
		return false;
	}

	private boolean buildEnabled() {
		int phase = this.handler.getPhase();
		if (phase == 2) {
			return true;
		}
		if (phase != 0) {
			return false;
		}
		if (!DreamTableHologram.hasPlacement()) {
			return false;
		}
		FarmTemplate template = FarmTemplates.get(this.handler.getSelectedFarm());
		if (template == null) {
			return false;
		}
		for (int index = 0; index < template.materials().size(); index++) {
			MaterialSpec spec = template.materials().get(index);
			if (this.handler.deposited(index) < spec.count()) {
				return false;
			}
		}
		return true;
	}

	private Text statusText() {
		return switch (this.handler.getPhase()) {
			case 1 -> Text.translatable("gui.the_dream_table.building", this.handler.getBuildIndex(), this.handler.getTotal());
			case 2 -> Text.translatable("gui.the_dream_table.blocked");
			case 3 -> Text.translatable("gui.the_dream_table.done");
			default -> {
				if (!DreamTableHologram.hasPlacement()) {
					yield Text.translatable("gui.the_dream_table.place_first");
				}
				yield buildEnabled()
						? Text.translatable("gui.the_dream_table.ready")
						: Text.translatable("gui.the_dream_table.need_materials");
			}
		};
	}

	private Text subtitle() {
		return switch (this.handler.getPage()) {
			case DreamTableScreenHandler.PAGE_WORK -> this.handler.getPhase() == 0
					? Text.translatable("gui.the_dream_table.aim")
					: Text.translatable("gui.the_dream_table.project");
			case DreamTableScreenHandler.PAGE_REQUIREMENTS -> Text.translatable("gui.the_dream_table.requirements");
			case DreamTableScreenHandler.PAGE_SCHEMES -> Text.translatable("gui.the_dream_table.schemes");
			case DreamTableScreenHandler.PAGE_OPTIONS -> Text.translatable("gui.the_dream_table.speed");
			default -> Text.translatable("gui.the_dream_table.choose");
		};
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (DreamTableKeys.ROTATE.matchesKey(keyCode, scanCode)) {
			DreamTableHologram.rotate();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	private void press(int id) {
		this.client.interactionManager.clickButton(this.handler.syncId, id);
		this.client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
	}

	private void updateHologram() {
		if (this.handler.getSelectedFarm().isEmpty()) {
			DreamTableHologram.clear();
			return;
		}
		DreamTableHologram.show(this.handler.getTablePos(), this.handler.getSelectedFarm(), this.handler.getBuildIndex(), this.handler.getPhase() == 2);
	}

	private void applyScroll() {
		if (this.handler.getPage() != scrolledPage) {
			scroll = 0;
			scrolledPage = this.handler.getPage();
			draggingScroll = false;
		}
		scroll = Math.max(0, Math.min(maxScroll(), scroll));
		int visible = visibleHeight();
		this.backgroundWidth = PANEL_WIDTH;
		this.backgroundHeight = visible;
		originX = (this.width - PANEL_WIDTH) / 2;
		originY = Math.max(4, (this.height - visible) / 2);
		this.x = originX;
		this.y = originY - scroll;
	}

	private int maxScroll() {
		return Math.max(0, contentHeight() - contentViewHeight());
	}

	private int visibleHeight() {
		int desired = HEADER + fixedContentView();
		int fit = Math.max(1, this.height - 8);
		return Math.min(desired, fit);
	}

	private int contentViewHeight() {
		return Math.max(1, visibleHeight() - HEADER);
	}

	private int fixedContentView() {
		return designView();
	}

	private int designView() {
		return PAD + 5 * CARD_HEIGHT + 4 * CARD_GAP + 4;
	}

	private int contentHeight() {
		return switch (this.handler.getPage()) {
			case DreamTableScreenHandler.PAGE_REQUIREMENTS -> Math.max(1, DreamTableLayout.requirementsHeight(materialCount()) - HEADER);
			case DreamTableScreenHandler.PAGE_WORK -> workContentHeight();
			case DreamTableScreenHandler.PAGE_SCHEMES -> schemesContentSpan();
			case DreamTableScreenHandler.PAGE_OPTIONS -> optionsContentHeight();
			default -> selectionContentHeight();
		};
	}

	private int selectionContentHeight() {
		int count = FarmOptions.ALL.size() + 2;
		return PAD + count * CARD_HEIGHT + (count - 1) * CARD_GAP + 6;
	}

	private int optionsContentHeight() {
		return PAD + 4 * CARD_HEIGHT + 3 * CARD_GAP + 6 + BUTTON_HEIGHT + 4;
	}

	private int workContentHeight() {
		int buttons = PAD + CARD_HEIGHT + 8 + 14 + 16;
		return buttons + (BUTTON_HEIGHT + 6) * 2 + BUTTON_HEIGHT + 8;
	}

	private int scrollTrackX() {
		return originX + PANEL_WIDTH - 15;
	}

	private int scrollTrackY() {
		return originY + HEADER + PAD;
	}

	private int scrollTrackHeight() {
		return Math.max(12, visibleHeight() - HEADER - PAD - 10);
	}

	private int thumbHeight() {
		int track = scrollTrackHeight();
		int view = Math.max(1, contentViewHeight());
		int content = Math.max(view, contentHeight());
		return Math.min(track, Math.max(28, track * view / content));
	}

	private int thumbY() {
		int travel = Math.max(1, scrollTrackHeight() - thumbHeight());
		int max = maxScroll();
		if (max <= 0) {
			return scrollTrackY();
		}
		return scrollTrackY() + travel * scroll / max;
	}

	private void dragScroll(double mouseY) {
		int travel = Math.max(1, scrollTrackHeight() - thumbHeight());
		int relative = (int) mouseY - dragGrab - scrollTrackY();
		int max = maxScroll();
		scroll = Math.max(0, Math.min(max, max * relative / travel));
	}

	private void drawScrollbar(DrawContext context, int mouseX, int mouseY) {
		if (maxScroll() <= 0) {
			return;
		}
		int x = scrollTrackX();
		int y = scrollTrackY();
		int height = scrollTrackHeight();
		int thumb = thumbHeight();
		int thumbTop = thumbY();
		boolean hovered = draggingScroll || inside(mouseX, mouseY, x - 2, thumbTop, 12, thumb);
		context.fill(x, y, x + TRACK_WIDTH, y + height, TRACK_EDGE);
		context.fill(x + 1, y + 1, x + TRACK_WIDTH - 1, y + height - 1, TRACK_FILL);
		context.fill(x + 1, y + 1, x + 2, y + height - 1, 0xFF0F4478);

		int left = x - 1;
		int right = x + TRACK_WIDTH + 1;
		ring(context, left, thumbTop, right - left, thumb, THUMB_EDGE);
		context.fill(left + 1, thumbTop + 1, right - 1, thumbTop + thumb - 1, hovered ? 0xFF4A92DC : THUMB_FILL);
		context.fill(left + 1, thumbTop + 1, right - 1, thumbTop + 2, THUMB_LIGHT);
		context.fill(left + 1, thumbTop + 1, left + 2, thumbTop + thumb - 1, THUMB_LIGHT);
		context.fill(right - 2, thumbTop + 2, right - 1, thumbTop + thumb - 1, THUMB_DARK);
		context.fill(left + 2, thumbTop + thumb - 2, right - 1, thumbTop + thumb - 1, THUMB_DARK);
		if (thumb >= 14) {
			int grip = thumbTop + thumb / 2;
			for (int line = -1; line <= 1; line++) {
				context.fill(x + 2, grip + line * 3, x + TRACK_WIDTH - 2, grip + line * 3 + 1, THUMB_EDGE);
			}
		}
	}

	private void clipToContent(DrawContext context) {
		int left = contentLeft();
		int top = contentTop();
		int right = contentRight();
		int bottom = contentBottom();
		if (right <= left || bottom <= top) {
			return;
		}
		context.enableScissor(left, top, right, bottom);
		clipContent = true;
	}

	private void releaseClip(DrawContext context) {
		if (!clipContent) {
			return;
		}
		context.disableScissor();
		clipContent = false;
	}

	private int materialCount() {
		FarmTemplate template = FarmTemplates.get(this.handler.getSelectedFarm());
		return template == null ? 0 : template.materials().size();
	}

	private int schemesContentHeight() {
		int count = FarmTemplates.imported().size();
		if (count == 0) {
			return 48;
		}
		return count * CARD_HEIGHT + (count - 1) * CARD_GAP;
	}

	private int schemesBackY() {
		return panelY() + HEADER + PAD + schemesContentHeight() + 10;
	}

	private int schemesContentSpan() {
		return PAD + schemesContentHeight() + 10 + BUTTON_HEIGHT + 8;
	}

	private int backButtonY() {
		return panelY() + DreamTableLayout.backY(materialCount());
	}

	private int cardY(int index) {
		return panelY() + HEADER + PAD + index * (CARD_HEIGHT + CARD_GAP);
	}

	private int cardX() {
		return panelX() + CARD_X;
	}

	private int cardWidth() {
		return CARD_WIDTH;
	}

	private int panelX() {
		return originX;
	}

	private int panelY() {
		return originY - scroll;
	}

	private int contentLeft() {
		return originX + 2;
	}

	private int contentRight() {
		return originX + PANEL_WIDTH - 17;
	}

	private int contentTop() {
		return originY + HEADER;
	}

	private int contentBottom() {
		return originY + visibleHeight() - 2;
	}

	private String trim(Text text, int width) {
		String raw = text.getString();
		if (this.textRenderer.getWidth(raw) <= width) {
			return raw;
		}
		String ellipsis = "...";
		return this.textRenderer.trimToWidth(raw, Math.max(0, width - this.textRenderer.getWidth(ellipsis))) + ellipsis;
	}

	private boolean hit(double mouseX, double mouseY, int x, int y, int width, int height) {
		if (mouseX < contentLeft() || mouseX >= contentRight() || mouseY < contentTop() || mouseY >= contentBottom()) {
			return false;
		}
		return inside(mouseX, mouseY, x, y, width, height);
	}

	private boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}
}
