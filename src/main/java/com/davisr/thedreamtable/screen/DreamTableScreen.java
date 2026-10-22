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
	private static final int CARD_HEIGHT = 30;
	private static final int CARD_GAP = 6;
	private static final int WORK_HEIGHT = HEADER + 10 + 5 * CARD_HEIGHT + 4 * CARD_GAP + 22;
	private static final int BUTTON_HEIGHT = DreamTableLayout.BUTTON;

	private static final int HEADER_COLOR = 0xFF2F6FA8;
	private static final int HEADER_LINE = 0xFF7EB0D8;
	private static final int PANEL = 0xFFF4F0E6;
	private static final int CARD = 0xFFFFFFFF;
	private static final int CARD_HOVER = 0xFFF3F8FC;
	private static final int CARD_SELECTED = 0xFFE4F0FA;
	private static final int CARD_BORDER = 0xFFE4DDD0;
	private static final int ACCENT = 0xFF2F6FA8;
	private static final int TITLE = 0xFFF7FBFF;
	private static final int SUBTITLE = 0xFFD5E6F5;
	private static final int NAME = 0xFF243140;
	private static final int HINT = 0xFF6D7B8A;
	private static final int MUTED = 0xFF9AA3AD;
	private static final int DISABLED = 0xFFE7E1D6;

	public DreamTableScreen(DreamTableScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = PANEL_WIDTH;
		this.backgroundHeight = WORK_HEIGHT;
		this.playerInventoryTitleY = 10_000;
		java.nio.file.Path worldRoot = this.client != null && this.client.getServer() != null
				? this.client.getServer().getSavePath(net.minecraft.util.WorldSavePath.ROOT)
				: null;
		FarmTemplates.reloadImports(worldRoot);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		this.backgroundWidth = DreamTableLayout.PANEL_WIDTH;
		this.backgroundHeight = panelHeight();
		updateHologram();
		super.render(context, mouseX, mouseY, delta);
		drawMouseoverTooltip(context, mouseX, mouseY);
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		int x = panelX();
		int y = panelY();
		int height = panelHeight();
		context.fill(x + 3, y + 4, x + PANEL_WIDTH + 3, y + height + 4, 0x2A1C2A38);
		context.fill(x, y, x + PANEL_WIDTH, y + height, PANEL);
		context.fill(x, y, x + PANEL_WIDTH, y + HEADER, HEADER_COLOR);
		context.fill(x, y + HEADER - 2, x + PANEL_WIDTH, y + HEADER, HEADER_LINE);
		context.drawBorder(x, y, PANEL_WIDTH, height, ACCENT);
		context.fill(x + 10, y + 14, x + 22, y + 26, 0xFFF7FBFF);
		context.fill(x + 13, y + 17, x + 19, y + 23, 0xFFD5E6F5);

		Text heading = Text.translatable("gui.the_dream_table.title");
		Text subtitle = subtitle();
		context.drawText(this.textRenderer, heading, x + 30, y + 10, TITLE, false);
		context.drawText(this.textRenderer, subtitle, x + 30, y + 24, SUBTITLE, false);

		switch (this.handler.getPage()) {
			case DreamTableScreenHandler.PAGE_WORK -> drawWork(context, mouseX, mouseY);
			case DreamTableScreenHandler.PAGE_REQUIREMENTS -> drawRequirements(context, mouseX, mouseY);
			case DreamTableScreenHandler.PAGE_SCHEMES -> drawSchemes(context, mouseX, mouseY);
			default -> drawSelection(context, mouseX, mouseY);
		}
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0 && this.client != null && this.client.interactionManager != null) {
			switch (this.handler.getPage()) {
				case DreamTableScreenHandler.PAGE_WORK -> {
					if (clickWork(mouseX, mouseY)) {
						return true;
					}
				}
				case DreamTableScreenHandler.PAGE_REQUIREMENTS -> {
					if (inside(mouseX, mouseY, cardX(), backButtonY(), cardWidth(), BUTTON_HEIGHT)) {
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
				default -> {
					for (int index = 0; index < FarmOptions.ALL.size(); index++) {
						if (inside(mouseX, mouseY, cardX(), cardY(index), cardWidth(), CARD_HEIGHT)) {
							FarmOption option = FarmOptions.ALL.get(index);
							this.handler.preview(option.id(), DreamTableScreenHandler.PAGE_WORK);
							press(index);
							return true;
						}
					}
					if (inside(mouseX, mouseY, cardX(), cardY(FarmOptions.ALL.size()), cardWidth(), CARD_HEIGHT)) {
						this.handler.preview(this.handler.getSelectedFarm(), DreamTableScreenHandler.PAGE_SCHEMES);
						press(DreamTableScreenHandler.BUTTON_SCHEMES);
						return true;
					}
				}
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	private void drawSelection(DrawContext context, int mouseX, int mouseY) {
		for (int index = 0; index < FarmOptions.ALL.size(); index++) {
			FarmOption option = FarmOptions.ALL.get(index);
			boolean selected = option.id().equals(this.handler.getSelectedFarm());
			drawOption(context, option.icon().getDefaultStack(), option.title(), cardX(), cardY(index), mouseX, mouseY, selected, true);
		}
		drawOption(context, new ItemStack(net.minecraft.item.Items.BOOK), Text.translatable("gui.the_dream_table.schemes"), cardX(), cardY(FarmOptions.ALL.size()), mouseX, mouseY, false, true);
	}

	private void drawSchemes(DrawContext context, int mouseX, int mouseY) {
		List<FarmOption> schemes = FarmTemplates.imported();
		if (schemes.isEmpty()) {
			Text empty = Text.translatable("gui.the_dream_table.schemes_empty");
			int textY = panelY() + HEADER + 18;
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
			if (inside(mouseX, mouseY, cardX(), cardY(index), cardWidth(), CARD_HEIGHT)) {
				this.handler.preview(schemes.get(index).id(), DreamTableScreenHandler.PAGE_WORK);
				press(DreamTableScreenHandler.BUTTON_SCHEME + index);
				return true;
			}
		}
		if (inside(mouseX, mouseY, cardX(), schemesBackY(), cardWidth(), BUTTON_HEIGHT)) {
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
		int x = panelX();
		int y = panelY() + HEADER + 12;
		context.fill(x + PANEL_WIDTH / 2 - 16, y, x + PANEL_WIDTH / 2 + 16, y + 32, CARD);
		context.drawBorder(x + PANEL_WIDTH / 2 - 16, y, 32, 32, ACCENT);
		context.drawItem(icon, x + PANEL_WIDTH / 2 - 8, y + 8);
		context.drawText(this.textRenderer, name, x + (PANEL_WIDTH - this.textRenderer.getWidth(name)) / 2, y + 38, NAME, false);
		Text status = statusText();
		context.drawText(this.textRenderer, status, x + (PANEL_WIDTH - this.textRenderer.getWidth(status)) / 2, y + 52, HINT, false);

		int barX = cardX();
		int barY = y + 66;
		context.fill(barX, barY, barX + cardWidth(), barY + 4, 0xFFE4DDD0);
		if (this.handler.getTotal() > 0) {
			int filled = cardWidth() * this.handler.getBuildIndex() / this.handler.getTotal();
			context.fill(barX, barY, barX + filled, barY + 4, ACCENT);
		}

		int buttons = barY + 14;
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
			ItemStack stack = slot.getStack();
			boolean complete = stack.isOf(spec.item()) && stack.getCount() >= spec.count();
			drawSlotFrame(context, slotX, slotY);
			if (complete) {
				context.drawBorder(slotX - 1, slotY - 1, 18, 18, ACCENT);
			}
			Text count = Text.translatable("gui.the_dream_table.count", stack.getCount(), spec.count());
			context.drawText(this.textRenderer, count, slotX + 8 - this.textRenderer.getWidth(count) / 2, slotY + 16, complete ? ACCENT : HINT, false);
		}
		drawAction(context, Text.translatable("gui.the_dream_table.back"), cardX(), backButtonY(), mouseX, mouseY, true, false);

		Text inventory = Text.translatable("gui.the_dream_table.inventory");
		context.drawText(this.textRenderer, inventory, panelX() + 16, panelY() + DreamTableLayout.inventoryY() - 12, HINT, false);
		for (int index = FarmTemplate.MAX_SLOTS; index < this.handler.slots.size(); index++) {
			Slot slot = this.handler.getSlot(index);
			drawSlotFrame(context, panelX() + slot.x, panelY() + slot.y);
		}
	}

	private void drawOption(DrawContext context, ItemStack icon, Text label, int x, int y, int mouseX, int mouseY, boolean selected, boolean enabled) {
		boolean hovered = enabled && inside(mouseX, mouseY, x, y, cardWidth(), CARD_HEIGHT);
		int fill = selected ? CARD_SELECTED : hovered ? CARD_HOVER : CARD;
		context.fill(x, y, x + cardWidth(), y + CARD_HEIGHT, fill);
		context.drawBorder(x, y, cardWidth(), CARD_HEIGHT, selected || hovered ? ACCENT : CARD_BORDER);
		if (selected) {
			context.fill(x, y, x + 3, y + CARD_HEIGHT, ACCENT);
			context.fill(x + cardWidth() - 14, y + 12, x + cardWidth() - 8, y + 18, ACCENT);
		}
		context.fill(x + 8, y + 6, x + 26, y + 24, 0xFFEFF5FA);
		context.drawItem(icon, x + 9, y + 7);
		context.drawText(this.textRenderer, label, x + 32, y + 11, NAME, false);
	}

	private void drawAction(DrawContext context, Text label, int x, int y, int mouseX, int mouseY, boolean enabled, boolean primary) {
		boolean hovered = enabled && inside(mouseX, mouseY, x, y, cardWidth(), BUTTON_HEIGHT);
		int fill = !enabled ? DISABLED : primary ? ACCENT : hovered ? CARD_HOVER : CARD;
		int border = !enabled ? 0xFFDDD4C6 : ACCENT;
		int color = !enabled ? MUTED : primary ? TITLE : NAME;
		context.fill(x, y, x + cardWidth(), y + BUTTON_HEIGHT, fill);
		context.drawBorder(x, y, cardWidth(), BUTTON_HEIGHT, border);
		if (enabled && !primary) {
			context.fill(x, y, x + 3, y + BUTTON_HEIGHT, ACCENT);
		}
		context.drawText(this.textRenderer, label, x + (cardWidth() - this.textRenderer.getWidth(label)) / 2, y + 10, color, false);
	}

	private void drawSlotFrame(DrawContext context, int x, int y) {
		context.fill(x - 1, y - 1, x + 17, y + 17, 0xFFD5E0EA);
		context.fill(x, y, x + 16, y + 16, 0xFFF8FBFE);
	}

	private boolean clickWork(double mouseX, double mouseY) {
		int buttons = panelY() + HEADER + 12 + 80;
		if (inside(mouseX, mouseY, cardX(), buttons, cardWidth(), BUTTON_HEIGHT)) {
			com.davisr.thedreamtable.client.DreamTableClipboard.pin(this.handler.getTablePos());
			press(DreamTableScreenHandler.BUTTON_REQUIREMENTS);
			if (this.client != null) {
				this.client.setScreen(null);
			}
			return true;
		}
		if (inside(mouseX, mouseY, cardX(), buttons + BUTTON_HEIGHT + 6, cardWidth(), BUTTON_HEIGHT)) {
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
		if (inside(mouseX, mouseY, cardX(), buttons + (BUTTON_HEIGHT + 6) * 2, cardWidth(), BUTTON_HEIGHT)) {
			this.handler.preview("", DreamTableScreenHandler.PAGE_SELECT);
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
			ItemStack stack = this.handler.getSlot(index).getStack();
			if (!stack.isOf(spec.item()) || stack.getCount() < spec.count()) {
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

	private int panelHeight() {
		if (this.handler.getPage() == DreamTableScreenHandler.PAGE_REQUIREMENTS) {
			return DreamTableLayout.requirementsHeight();
		}
		if (this.handler.getPage() == DreamTableScreenHandler.PAGE_WORK) {
			return WORK_HEIGHT;
		}
		if (this.handler.getPage() == DreamTableScreenHandler.PAGE_SCHEMES) {
			return schemesHeight();
		}
		int count = FarmOptions.ALL.size() + 1;
		return HEADER + 10 + count * CARD_HEIGHT + (count - 1) * CARD_GAP + 22;
	}

	private int schemesContentHeight() {
		int count = FarmTemplates.imported().size();
		if (count == 0) {
			return 64;
		}
		return count * CARD_HEIGHT + (count - 1) * CARD_GAP;
	}

	private int schemesBackY() {
		return panelY() + HEADER + 10 + schemesContentHeight() + 10;
	}

	private int schemesHeight() {
		return HEADER + 10 + schemesContentHeight() + 10 + BUTTON_HEIGHT + 16;
	}

	private int backButtonY() {
		return panelY() + DreamTableLayout.backY();
	}

	private int cardY(int index) {
		return panelY() + HEADER + 10 + index * (CARD_HEIGHT + CARD_GAP);
	}

	private int cardX() {
		return panelX() + 10;
	}

	private int cardWidth() {
		return PANEL_WIDTH - 20;
	}

	private int panelX() {
		return (this.width - PANEL_WIDTH) / 2;
	}

	private int panelY() {
		return (this.height - this.backgroundHeight) / 2;
	}

	private boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}
}
