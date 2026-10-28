package com.davisr.thedreamtable.screen.page;

import com.davisr.thedreamtable.block.entity.BuildIssue;
import com.davisr.thedreamtable.client.DreamTableClipboard;
import com.davisr.thedreamtable.client.DreamTableHologram;
import com.davisr.thedreamtable.farm.BuildPace;
import com.davisr.thedreamtable.farm.FarmOptions;
import com.davisr.thedreamtable.farm.FarmOptions.FarmOption;
import com.davisr.thedreamtable.farm.FarmTemplates;
import com.davisr.thedreamtable.farm.ImportNote;
import com.davisr.thedreamtable.network.PlaceFarmPayload;
import com.davisr.thedreamtable.platform.Platform;
import com.davisr.thedreamtable.screen.DreamTableScreenHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;

import static com.davisr.thedreamtable.screen.ui.Theme.*;

public final class WorkPage implements TablePage {
	private static final int MENU = 0;
	private static final int REQUIREMENTS = 1;
	private static final int MATERIALS = 2;
	private static final int BUILD = 3;
	private static final int DISCARD = 4;
	private static final int CONFIRM_DISCARD = 5;
	private static final long CONFIRM_MS = 3000L;
	/** From this much, the spot already holds this farm, likely one finished before finished farms were protected. */
	private static final int EXISTING_FARM_PERCENT = 50;
	private static final int LINE_HEIGHT = 10;
	/** Room for why a build stopped and what to do about it. Kept even when the status is short, so buttons don't jump. */
	private static final int STATUS_LINES = 4;
	/** A farm with several warnings may need more, up to this. */
	private static final int MAX_STATUS_LINES = 9;

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
		ui.cardAt("farm", context, icon, name, flowX(), ui.view().cardY(0), mouseX, mouseY, true, true);
		drawStatus(context);
		ui.painter().progress(context, flowX(), barY(), CARD_WIDTH, handler.getBuildIndex(), handler.getTotal());

		ui.buttonAt("menu", context, Text.translatable("gui.the_dream_table.menu"), flowX(), rowY(MENU), mouseX, mouseY, true, false);
		ui.buttonAt("requirements", context, Text.translatable("gui.the_dream_table.requirements"), flowX(), rowY(REQUIREMENTS), mouseX, mouseY, true, false);
		ui.buttonAt("materials", context, Text.translatable("gui.the_dream_table.materials"), flowX(), rowY(MATERIALS), mouseX, mouseY, true, false);
		ui.buttonAt("build", context, buildLabel(), flowX(), rowY(BUILD), mouseX, mouseY, ProjectState.canBuild(handler), true);
		if (confirming()) {
			ui.buttonAt("cancel_discard", context, Text.translatable("gui.the_dream_table.cancel"), flowX(), rowY(DISCARD), mouseX, mouseY, true, false);
			ui.destructiveButtonAt("confirm_discard", context, Text.translatable("gui.the_dream_table.confirm_discard"), flowX(), rowY(CONFIRM_DISCARD), mouseX, mouseY, true);
		} else {
			ui.destructiveButtonAt("discard", context, Text.translatable("gui.the_dream_table.discard"), flowX(), rowY(DISCARD), mouseX, mouseY, true);
		}
	}

	@Override
	public boolean click(double mouseX, double mouseY) {
		if (ui.buttonHit(mouseX, mouseY, flowX(), rowY(MENU))) {
			ui.go(DreamTableScreenHandler.PAGE_SELECT, DreamTableScreenHandler.BUTTON_MENU);
			return true;
		}
		if (ui.buttonHit(mouseX, mouseY, flowX(), rowY(REQUIREMENTS))) {
			DreamTableClipboard.pin(ui.handler().getTablePos());
			ui.actions().press(DreamTableScreenHandler.BUTTON_REQUIREMENTS);
			ui.actions().close();
			return true;
		}
		if (ui.buttonHit(mouseX, mouseY, flowX(), rowY(MATERIALS))) {
			ui.go(DreamTableScreenHandler.PAGE_MATERIALS, DreamTableScreenHandler.BUTTON_MATERIALS);
			return true;
		}
		if (ui.buttonHit(mouseX, mouseY, flowX(), rowY(BUILD))) {
			build();
			return true;
		}
		if (ui.buttonHit(mouseX, mouseY, flowX(), rowY(DISCARD))) {
			if (confirming()) {
				confirmUntil = 0L;
				ui.actions().click();
			} else {
				requestDiscard();
			}
			return true;
		}
		if (confirming() && ui.buttonHit(mouseX, mouseY, flowX(), rowY(CONFIRM_DISCARD))) {
			discardNow();
			return true;
		}
		return false;
	}

	/** Discarding a started build takes it apart, so that needs a second click within a few seconds. */
	private void requestDiscard() {
		boolean started = ui.handler().getBuildIndex() > 0 && ui.handler().getPhase() != ProjectState.DONE;
		if (started) {
			confirmUntil = Util.getMeasuringTimeMs() + CONFIRM_MS;
			ui.actions().click();
			ui.message("gui.the_dream_table.discard_warning");
			return;
		}
		discardNow();
	}

	private void discardNow() {
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
		int lastRow = confirming() ? CONFIRM_DISCARD : DISCARD;
		return rowY(lastRow) - ui.view().cardY(0) + PAD + BUTTON_HEIGHT + 8;
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
		Platform.clientNetwork().send(new PlaceFarmPayload(ui.handler().getTablePos(), DreamTableHologram.anchorForBuild(), DreamTableHologram.previewFacing()));
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

	/**
	 * The status, or, when the build stopped or waits for a reason, what happened in dark text and what to do in grey,
	 * wrapped. Before an imported farm is started, its warnings come first, so nothing is found out halfway through.
	 */
	private void drawStatus(DrawContext context) {
		List<Integer> colors = new ArrayList<>();
		List<OrderedText> lines = statusLines(colors);
		for (int line = 0; line < Math.min(MAX_STATUS_LINES, lines.size()); line++) {
			context.drawText(ui.painter().text(), lines.get(line), flowX(), statusY() + line * LINE_HEIGHT, colors.get(line), false);
		}
	}

	/** The wrapped status lines, with each line's color added to {@code colors}. */
	private List<OrderedText> statusLines(List<Integer> colors) {
		BuildIssue issue = currentIssue();
		List<OrderedText> lines = new ArrayList<>();
		if (issue == null) {
			if (ui.handler().getPhase() == ProjectState.IDLE) {
				for (ImportNote note : FarmTemplates.notes(ui.farm())) {
					addLines(lines, colors, note.text(), NAME);
				}
			}
			addLines(lines, colors, status(), HINT);
		} else {
			addLines(lines, colors, issue.what(), NAME);
			addLines(lines, colors, issue.fix(), HINT);
		}
		return lines;
	}

	private int statusHeight() {
		int count = statusLines(new ArrayList<>()).size();
		return Math.max(STATUS_LINES, Math.min(MAX_STATUS_LINES, count)) * LINE_HEIGHT;
	}

	private void addLines(List<OrderedText> lines, List<Integer> colors, Text text, int color) {
		for (OrderedText line : ui.painter().text().wrapLines(text, CARD_WIDTH)) {
			lines.add(line);
			colors.add(color);
		}
	}

	/** The issue matters while the build is stopped, or while it waits on a slow chunk; an old one from a finished step doesn't. */
	private BuildIssue currentIssue() {
		int phase = ui.handler().getPhase();
		return phase == ProjectState.BLOCKED || phase == ProjectState.WAITING || phase == ProjectState.BUILDING ? ui.handler().getIssue() : null;
	}

	private Text status() {
		DreamTableScreenHandler handler = ui.handler();
		return switch (handler.getPhase()) {
			case ProjectState.BUILDING -> withTimeLeft(Text.translatable("gui.the_dream_table.building", handler.getBuildIndex(), handler.getTotal()));
			case ProjectState.BLOCKED -> Text.translatable("gui.the_dream_table.blocked");
			case ProjectState.DONE -> Text.translatable("gui.the_dream_table.done");
			case ProjectState.WAITING -> Text.translatable("gui.the_dream_table.paused");
			case ProjectState.PAUSED -> withTimeLeft(Text.translatable("gui.the_dream_table.paused_build", handler.getBuildIndex(), handler.getTotal()));
			default -> idleStatus();
		};
	}

	/** Adds the estimate at the actual rate, like "~4 min". Blocks already in place are skipped, so it can finish sooner. */
	private Text withTimeLeft(Text status) {
		DreamTableScreenHandler handler = ui.handler();
		int seconds = BuildPace.secondsLeftAt(handler.getBlocksPerSecond(), handler.getTotal(), handler.getBuildIndex());
		return Text.translatable("gui.the_dream_table.with_eta", status, TimeText.of(seconds));
	}

	private Text idleStatus() {
		if (!DreamTableHologram.hasPlacement()) {
			return Text.translatable("gui.the_dream_table.place_first");
		}
		String problem = DreamTableHologram.placementProblem();
		if (!problem.isEmpty()) {
			return Text.translatable(problem);
		}
		Text dimension = wrongDimension();
		if (dimension != null) {
			return Text.translatable("gui.the_dream_table.wrong_dimension", dimension);
		}
		if (lowMobFarm()) {
			return Text.translatable("gui.the_dream_table.mob_low");
		}
		int existing = DreamTableHologram.existingPercent();
		if (existing >= EXISTING_FARM_PERCENT) {
			return Text.translatable("gui.the_dream_table.existing_farm", existing);
		}
		if (ui.actions().creative()) {
			return Text.translatable("gui.the_dream_table.creative");
		}
		return ProjectState.hasAllMaterials(ui.handler())
				? Text.translatable("gui.the_dream_table.ready")
				: Text.translatable("gui.the_dream_table.partial");
	}

	/** The dimension a farm was designed for when the player is placing it elsewhere, or null; farms without one never warn. */
	private Text wrongDimension() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null) {
			return null;
		}
		return FarmOptions.byId(ui.farm())
				.filter(option -> option.outsideIntendedDimension(client.world.getRegistryKey()))
				.map(option -> LoadedFarmsPage.dimension(option.intendedDimension().getValue().toString()))
				.orElse(null);
	}

	/** Mob farms below sea level fill with bats and glow squids and compete with caves for spawns. */
	private boolean lowMobFarm() {
		MinecraftClient client = MinecraftClient.getInstance();
		boolean mobs = FarmOptions.Category.of(ui.farm()).filter(category -> category == FarmOptions.Category.MOBS).isPresent();
		return mobs && client.world != null && DreamTableHologram.anchorForBuild().getY() < client.world.getSeaLevel();
	}

	private int statusY() {
		return ui.view().cardY(0) + CARD_HEIGHT + 8;
	}

	private int flowX() {
		return ui.view().flowX();
	}

	private int barY() {
		return statusY() + statusHeight() + 4;
	}

	private int rowY(int row) {
		return barY() + 16 + row * (BUTTON_HEIGHT + BUTTON_GAP);
	}

}
