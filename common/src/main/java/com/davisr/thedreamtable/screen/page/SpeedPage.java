package com.davisr.thedreamtable.screen.page;

import com.davisr.thedreamtable.farm.BuildPace;
import com.davisr.thedreamtable.screen.DreamTableScreenHandler;
import com.davisr.thedreamtable.screen.ui.Adjustment;
import com.davisr.thedreamtable.screen.ui.ControlId;
import com.davisr.thedreamtable.screen.ui.layout.UiRect;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import static com.davisr.thedreamtable.screen.ui.Theme.*;

/**
 * Slow, Normal and Fast, each a share of the server's fastest rate, then Custom with a slider from 1% to 100%.
 * Each second line says the share and how long the chosen farm would take. The slider only sends its value
 * when released, so dragging doesn't flood the server. The keyboard can focus it too: Left and Right step the
 * percentage by one, Home and End jump to 1% and 100%, and the value is sent once the keys rest, for the same reason.
 */
public final class SpeedPage implements TablePage {
	private static final Level[] LEVELS = {
			new Level(BuildPace.SLOW, Items.CLOCK, "gui.the_dream_table.pace_slow"),
			new Level(BuildPace.NORMAL, Items.REPEATER, "gui.the_dream_table.pace_normal"),
			new Level(BuildPace.FAST, Items.REDSTONE_BLOCK, "gui.the_dream_table.pace_fast"),
	};
	private static final int SLIDER_INSET = 6;
	private static final int SLIDER_HEIGHT = 12;
	private static final String SLIDER = "pace.slider";
	/** How long the arrow keys must rest before the percentage is sent, so holding a key sends one value, not dozens. */
	private static final long KEY_SETTLE_MS = 250L;

	private final PageContext ui;
	/** The percentage under the knob while it is held; -1 when not dragging. */
	private int dragging = -1;
	/** The percentage chosen with the keyboard and not sent yet; -1 when none is waiting. */
	private int keyed = -1;
	private long keyedAt;

	public SpeedPage(PageContext ui) {
		this.ui = ui;
	}

	private record Level(int pace, Item icon, String label) {
	}

	/** The name of the chosen level, or Custom, for the Build Speed card on the Options page. */
	static Text chosen(DreamTableScreenHandler handler) {
		for (Level level : LEVELS) {
			if (level.pace() == handler.getPace()) {
				return Text.translatable(level.label());
			}
		}
		return Text.translatable("gui.the_dream_table.pace_custom");
	}

	/** "25% of max · ~40 s" for the chosen farm's remaining blocks, or just the share when no farm is chosen. */
	static Text detail(DreamTableScreenHandler handler, int pace) {
		Text share = Text.translatable("gui.the_dream_table.pace_share", BuildPace.percent(pace));
		if (handler.getTotal() <= 0) {
			return share;
		}
		int seconds = BuildPace.secondsLeftAt(handler.blocksPerSecondAt(pace), handler.getTotal(), handler.getBuildIndex());
		return Text.translatable("gui.the_dream_table.with_eta", share, TimeText.of(seconds));
	}

	@Override
	public void draw(DrawContext context, int mouseX, int mouseY) {
		DreamTableScreenHandler handler = ui.handler();
		sendKeyed(false);
		int pace = shownPace();
		for (int index = 0; index < LEVELS.length; index++) {
			Level level = LEVELS[index];
			ui.cardAt("pace." + index, context, new ItemStack(level.icon()), Text.translatable(level.label()), detail(handler, level.pace()),
					flowX(), ui.view().cardY(index), flowWidth(), mouseX, mouseY, pace == level.pace(), true);
		}
		ui.cardAt("pace.custom", context, new ItemStack(Items.COMPARATOR), Text.translatable("gui.the_dream_table.pace_custom"), detail(handler, pace),
				flowX(), customY(), flowWidth(), mouseX, mouseY, !BuildPace.isLevel(pace), true);
		float fraction = (BuildPace.percent(pace) - BuildPace.MIN_PERCENT) / (float) (BuildPace.MAX_PERCENT - BuildPace.MIN_PERCENT);
		ui.painter().slider(context, sliderX(), sliderY(), sliderWidth(), fraction, dragging >= 0 || !BuildPace.isLevel(pace));
		ui.sliderAt(SLIDER, context, Text.translatable("gui.the_dream_table.pace_slider"), sliderValue(pace),
				new UiRect(sliderX() - 4, sliderY() - 3, sliderWidth() + 8, SLIDER_HEIGHT + 6), () -> choose(BuildPace.percent(shownPace())));
		ui.buttonAt("back", context, Text.translatable("gui.the_dream_table.back"), flowX(), backY(), mouseX, mouseY, true, false);
	}

	@Override
	public boolean click(double mouseX, double mouseY) {
		for (int index = 0; index < LEVELS.length; index++) {
			if (ui.cardHit(mouseX, mouseY, flowX(), ui.view().cardY(index), flowWidth())) {
				keyed = -1;
				ui.handler().previewPace(LEVELS[index].pace());
				ui.actions().press(DreamTableScreenHandler.BUTTON_PACE + index);
				return true;
			}
		}
		if (ui.view().hit(mouseX, mouseY, sliderX() - 4, sliderY() - 3, sliderWidth() + 8, SLIDER_HEIGHT + 6)) {
			dragging = percentAt(mouseX);
			return true;
		}
		if (ui.cardHit(mouseX, mouseY, flowX(), customY(), flowWidth())) {
			choose(BuildPace.percent(ui.handler().getPace()));
			return true;
		}
		if (ui.buttonHit(mouseX, mouseY, flowX(), backY())) {
			sendKeyed(true);
			ui.go(DreamTableScreenHandler.PAGE_OPTIONS, DreamTableScreenHandler.BUTTON_SPEED_BACK);
			return true;
		}
		return false;
	}

	@Override
	public boolean drag(double mouseX, double mouseY) {
		if (dragging < 0) {
			return false;
		}
		dragging = percentAt(mouseX);
		return true;
	}

	@Override
	public boolean release(double mouseX, double mouseY) {
		if (dragging < 0) {
			return false;
		}
		int percent = percentAt(mouseX);
		dragging = -1;
		choose(percent);
		return true;
	}

	@Override
	public boolean adjust(ControlId control, Adjustment how) {
		if (!control.equals(ui.idOf(SLIDER))) {
			return false;
		}
		keyed = stepped(BuildPace.percent(shownPace()), how);
		keyedAt = Util.getMeasuringTimeMs();
		return true;
	}

	@Override
	public Text adjusted(ControlId control) {
		return control.equals(ui.idOf(SLIDER)) ? sliderValue(shownPace()) : Text.empty();
	}

	/** The percentage after a key: one step, or an end of the range, never outside it. */
	static int stepped(int percent, Adjustment how) {
		return how.apply(percent, 1, BuildPace.MIN_PERCENT, BuildPace.MAX_PERCENT);
	}

	private static Text sliderValue(int pace) {
		return Text.translatable("gui.the_dream_table.pace_share", BuildPace.percent(pace));
	}

	@Override
	public void flush() {
		sendKeyed(true);
	}

	/** Sends the keyboard's percentage once the keys have rested, or right away when {@code now}. */
	private void sendKeyed(boolean now) {
		if (keyed >= 0 && (now || Util.getMeasuringTimeMs() - keyedAt >= KEY_SETTLE_MS)) {
			choose(keyed);
		}
	}

	private void choose(int percent) {
		keyed = -1;
		ui.handler().previewPace(BuildPace.ofPercent(percent));
		ui.actions().press(DreamTableScreenHandler.BUTTON_PACE_CUSTOM + percent);
	}

	/** The knob follows the mouse while held, ahead of the server, whose synced pace would pull it back mid-drag. */
	private int shownPace() {
		if (dragging >= 0) {
			return BuildPace.ofPercent(dragging);
		}
		return keyed >= 0 ? BuildPace.ofPercent(keyed) : ui.handler().getPace();
	}

	private int percentAt(double mouseX) {
		double fraction = (mouseX - sliderX() - 3) / (sliderWidth() - 6);
		int span = BuildPace.MAX_PERCENT - BuildPace.MIN_PERCENT;
		return BuildPace.MIN_PERCENT + (int) Math.round(Math.max(0.0, Math.min(1.0, fraction)) * span);
	}

	@Override
	public int contentHeight() {
		int cards = LEVELS.length + 1;
		return PAD + cards * CARD_HEIGHT + (cards - 1) * CARD_GAP + 8 + SLIDER_HEIGHT + 10 + BUTTON_HEIGHT + 4;
	}

	@Override
	public Text subtitle() {
		return Text.translatable("gui.the_dream_table.speed");
	}

	private int customY() {
		return ui.view().cardY(LEVELS.length);
	}

	private int sliderX() {
		return flowX() + SLIDER_INSET;
	}

	private int sliderWidth() {
		return flowWidth() - 2 * SLIDER_INSET;
	}

	private int sliderY() {
		return customY() + CARD_HEIGHT + 8;
	}

	private int backY() {
		return sliderY() + SLIDER_HEIGHT + 10;
	}

	private int flowX() {
		return ui.view().flowX();
	}

	private int flowWidth() {
		return ui.view().flowWidth();
	}
}
