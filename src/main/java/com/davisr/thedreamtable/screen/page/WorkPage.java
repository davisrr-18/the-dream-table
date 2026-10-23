package com.davisr.thedreamtable.screen.page;

import com.davisr.thedreamtable.client.DreamTableClipboard;
import com.davisr.thedreamtable.client.DreamTableHologram;
import com.davisr.thedreamtable.farm.FarmOptions;
import com.davisr.thedreamtable.farm.FarmOptions.FarmOption;
import com.davisr.thedreamtable.network.PlaceFarmPayload;
import com.davisr.thedreamtable.screen.DreamTableScreenHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import static com.davisr.thedreamtable.screen.ui.Theme.*;

public final class WorkPage implements TablePage {
	private static final int MENU = 0;
	private static final int REQUIREMENTS = 1;
	private static final int MATERIALS = 2;
	private static final int BUILD = 3;
	private static final int DISCARD = 4;
	private static final int ROWS = 5;
	private static final long CONFIRM_MS = 3000L;

	private final PageContext ui;
	private long confirmUntil;

	public WorkPage(PageContext ui) {
		this.ui = ui;
	}

	@Override
	public void draw(DrawContext context, int mouseX, int mouseY) {
		DreamTableScreenHandler handler = ui.handler();
		FarmOption option = FarmOptions.byId(ui.farm()).orElse(null);
		ItemStack icon = option == null ? new ItemStack(Items.STRUCTURE_BLOCK) : option.icon().getDefaultStack();
		Text name = option == null ? Text.literal(ui.farm()) : option.title();
		ui.card(context, icon, name, ui.view().cardY(0), mouseX, mouseY, true, true);
		context.drawText(ui.painter().text(), ui.painter().trim(status(), CARD_WIDTH), ui.view().cardX(), statusY(), HINT, false);
		ui.painter().progress(context, ui.view().cardX(), barY(), CARD_WIDTH, handler.getBuildIndex(), handler.getTotal());

		ui.button(context, Text.translatable("gui.the_dream_table.menu"), rowY(MENU), mouseX, mouseY, true, false);
		ui.button(context, Text.translatable("gui.the_dream_table.requirements"), rowY(REQUIREMENTS), mouseX, mouseY, true, false);
		ui.button(context, Text.translatable("gui.the_dream_table.materials"), rowY(MATERIALS), mouseX, mouseY, true, false);
		ui.button(context, buildLabel(), rowY(BUILD), mouseX, mouseY, ProjectState.canBuild(handler), true);
		Text discard = confirming() ? Text.translatable("gui.the_dream_table.discard_confirm") : Text.translatable("gui.the_dream_table.discard");
		ui.button(context, discard, rowY(DISCARD), mouseX, mouseY, true, confirming());
	}

	@Override
	public boolean click(double mouseX, double mouseY) {
		if (ui.buttonHit(mouseX, mouseY, rowY(MENU))) {
			ui.go(DreamTableScreenHandler.PAGE_SELECT, DreamTableScreenHandler.BUTTON_MENU);
			return true;
		}
		if (ui.buttonHit(mouseX, mouseY, rowY(REQUIREMENTS))) {
			DreamTableClipboard.pin(ui.handler().getTablePos());
			ui.actions().press(DreamTableScreenHandler.BUTTON_REQUIREMENTS);
			ui.actions().close();
			return true;
		}
		if (ui.buttonHit(mouseX, mouseY, rowY(MATERIALS))) {
			ui.go(DreamTableScreenHandler.PAGE_MATERIALS, DreamTableScreenHandler.BUTTON_MATERIALS);
			return true;
		}
		if (ui.buttonHit(mouseX, mouseY, rowY(BUILD))) {
			build();
			return true;
		}
		if (ui.buttonHit(mouseX, mouseY, rowY(DISCARD))) {
			discard();
			return true;
		}
		return false;
	}

	/** Discarding a started build takes it apart, so that needs a second click within a few seconds. */
	private void discard() {
		boolean started = ui.handler().getBuildIndex() > 0 && ui.handler().getPhase() != ProjectState.DONE;
		if (started && !confirming()) {
			confirmUntil = Util.getMeasuringTimeMs() + CONFIRM_MS;
			ui.actions().click();
			ui.message("gui.the_dream_table.discard_warning");
			return;
		}
		confirmUntil = 0L;
		ui.handler().preview("", DreamTableScreenHandler.PAGE_SELECT);
		DreamTableHologram.clear();
		DreamTableClipboard.close();
		ui.actions().press(DreamTableScreenHandler.BUTTON_DISCARD);
	}

	private boolean confirming() {
		return Util.getMeasuringTimeMs() < confirmUntil;
	}

	@Override
	public int contentHeight() {
		return buttonsOffset() + (BUTTON_HEIGHT + BUTTON_GAP) * (ROWS - 1) + BUTTON_HEIGHT + 8;
	}

	@Override
	public Text subtitle() {
		return ui.handler().getPhase() == ProjectState.IDLE
				? Text.translatable("gui.the_dream_table.aim")
				: Text.translatable("gui.the_dream_table.project");
	}

	private void build() {
		if (!ProjectState.canBuild(ui.handler())) {
			return;
		}
		if (ui.handler().getPhase() == ProjectState.BUILDING) {
			ui.actions().press(DreamTableScreenHandler.BUTTON_PAUSE);
			return;
		}
		if (ui.handler().getPhase() == ProjectState.IDLE && !DreamTableHologram.hasPlacement()) {
			ui.actions().click();
			ui.actions().close();
			ui.message("gui.the_dream_table.place_then_build");
			return;
		}
		ClientPlayNetworking.send(new PlaceFarmPayload(ui.handler().getTablePos(), DreamTableHologram.anchorForBuild(), DreamTableHologram.previewFacing()));
		ui.actions().click();
	}

	private Text buildLabel() {
		return switch (ui.handler().getPhase()) {
			case ProjectState.BUILDING -> Text.translatable("gui.the_dream_table.pause");
			case ProjectState.BLOCKED -> Text.translatable("gui.the_dream_table.retry");
			case ProjectState.DONE -> Text.translatable("gui.the_dream_table.done_short");
			case ProjectState.WAITING -> Text.translatable("gui.the_dream_table.continue");
			case ProjectState.PAUSED -> Text.translatable("gui.the_dream_table.resume");
			default -> Text.translatable("gui.the_dream_table.build");
		};
	}

	private Text status() {
		DreamTableScreenHandler handler = ui.handler();
		return switch (handler.getPhase()) {
			case ProjectState.BUILDING -> Text.translatable("gui.the_dream_table.building", handler.getBuildIndex(), handler.getTotal());
			case ProjectState.BLOCKED -> Text.translatable("gui.the_dream_table.blocked");
			case ProjectState.DONE -> Text.translatable("gui.the_dream_table.done");
			case ProjectState.WAITING -> Text.translatable("gui.the_dream_table.paused");
			case ProjectState.PAUSED -> Text.translatable("gui.the_dream_table.paused_build", handler.getBuildIndex(), handler.getTotal());
			default -> idleStatus();
		};
	}

	private Text idleStatus() {
		if (!DreamTableHologram.hasPlacement()) {
			return Text.translatable("gui.the_dream_table.place_first");
		}
		String problem = DreamTableHologram.placementProblem();
		if (!problem.isEmpty()) {
			return Text.translatable(problem);
		}
		if (ui.actions().creative()) {
			return Text.translatable("gui.the_dream_table.creative");
		}
		return ProjectState.hasAllMaterials(ui.handler())
				? Text.translatable("gui.the_dream_table.ready")
				: Text.translatable("gui.the_dream_table.partial");
	}

	private int statusY() {
		return ui.view().cardY(0) + CARD_HEIGHT + 8;
	}

	private int barY() {
		return statusY() + 14;
	}

	private int rowY(int row) {
		return barY() + 16 + row * (BUTTON_HEIGHT + BUTTON_GAP);
	}

	private static int buttonsOffset() {
		return PAD + CARD_HEIGHT + 8 + 14 + 16;
	}
}
