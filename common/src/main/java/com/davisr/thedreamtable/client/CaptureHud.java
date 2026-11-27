package com.davisr.thedreamtable.client;

import com.davisr.thedreamtable.client.map.KeyChips;
import com.davisr.thedreamtable.client.map.MapControls;
import com.davisr.thedreamtable.farm.capture.CaptureBox;
import com.davisr.thedreamtable.farm.capture.CaptureProblem;
import com.davisr.thedreamtable.farm.capture.CaptureSteps;
import com.davisr.thedreamtable.farm.capture.CaptureSteps.Hint;
import com.davisr.thedreamtable.farm.capture.CaptureSteps.State;
import com.davisr.thedreamtable.farm.capture.CaptureSteps.Step;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The capture's panel at the top left while the mode is on, in the look of map mode's controls bar ({@link KeyChips}, the same
 * margin, padding, row height, colours and key chips): the three steps with the finished ones ticked and the current one
 * highlighted, the size, the keys of the current step, and one red line for what is wrong.
 */
final class CaptureHud {
	private static final int MARGIN = 6;
	private static final int PADDING = KeyChips.PADDING;
	private static final int ROW = KeyChips.ROW;
	private static final int LINE = 11;
	/** The widest the panel's content gets, in scaled pixels. */
	private static final int MAX_INNER = 200;
	/** The width of the column the step marks sit in. */
	private static final int MARK_COLUMN = 12;
	private static final int TEXT = 0xFFFFFFFF;
	private static final int PENDING = 0xFF8A8A8A;
	private static final int DONE_MARK = 0xFF7CD67C;
	private static final int DONE_TEXT = 0xFFD8D8D8;
	private static final int SIZE = 0xFFFFE066;
	private static final int PROBLEM = 0xFFFF7777;
	private static final int CURRENT_FILL = 0x558CD8FF;

	private CaptureHud() {
	}

	private record Chip(Text key, Text action) {
	}

	static void render(DrawContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (!CaptureMode.active() || client.options.hudHidden) {
			return;
		}
		TextRenderer text = client.textRenderer;
		// Narrow enough to stay clear of the centred table notice and tracked-farm line at any GUI scale; longer text wraps.
		int maxInner = Math.max(40, Math.min(MAX_INNER, context.getScaledWindowWidth() - 2 * (MARGIN + PADDING)));
		Step current = CaptureSteps.current(CaptureMode.selection());
		List<OrderedText> title = text.wrapLines(Text.translatable("gui.the_dream_table.capture_title"), maxInner);
		List<List<OrderedText>> steps = List.of(wrapStep(text, "first", maxInner), wrapStep(text, "second", maxInner), wrapStep(text, "adjust", maxInner));
		Text sizeText = sizeText();
		List<OrderedText> size = sizeText == null ? List.of() : text.wrapLines(sizeText, maxInner);
		List<Chip> chips = chips(client, current);
		int[] widths = new int[chips.size()];
		for (int index = 0; index < widths.length; index++) {
			widths[index] = KeyChips.width(text, chips.get(index).key(), chips.get(index).action());
		}
		List<List<Integer>> rows = MapControls.rows(widths, maxInner, KeyChips.ITEM_GAP);
		CaptureProblem problem = CaptureMode.problem();
		List<OrderedText> problemLines = problem == null ? List.of() : text.wrapLines(problem.text(), maxInner);

		int inner = widest(text, title);
		inner = Math.max(inner, widest(text, size));
		for (List<OrderedText> step : steps) {
			inner = Math.max(inner, MARK_COLUMN + widest(text, step));
		}
		for (List<Integer> row : rows) {
			inner = Math.max(inner, MapControls.rowWidth(row, widths, KeyChips.ITEM_GAP));
		}
		inner = Math.max(inner, widest(text, problemLines));
		int height = blockHeight(title) + blockHeight(size) + rows.size() * ROW + LINE * problemLines.size() + 2 * PADDING - (ROW - 12);
		for (List<OrderedText> step : steps) {
			height += blockHeight(step);
		}
		context.fill(MARGIN, MARGIN, MARGIN + inner + 2 * PADDING, MARGIN + height, KeyChips.PANEL_COLOR);

		int x = MARGIN + PADDING;
		int y = MARGIN + PADDING;
		y = drawBlock(context, text, title, x, y, KeyChips.KEY_BORDER);
		for (int index = 0; index < steps.size(); index++) {
			State state = CaptureSteps.stateOf(Step.values()[index], current);
			if (state == State.CURRENT) {
				context.fill(x - 3, y - 1, x + inner + 3, y - 1 + blockHeight(steps.get(index)) - (ROW - 12), CURRENT_FILL);
			}
			context.drawTextWithShadow(text, mark(state), x, y + 2, state == State.DONE ? DONE_MARK : state == State.CURRENT ? TEXT : PENDING);
			y = drawBlock(context, text, steps.get(index), x + MARK_COLUMN, y, state == State.CURRENT ? TEXT : state == State.DONE ? DONE_TEXT : PENDING);
		}
		y = drawBlock(context, text, size, x, y, SIZE);
		for (List<Integer> row : rows) {
			int chipX = x;
			for (int index : row) {
				KeyChips.draw(context, text, chips.get(index).key(), chips.get(index).action(), chipX, y);
				chipX += widths[index] + KeyChips.ITEM_GAP;
			}
			y += ROW;
		}
		for (OrderedText line : problemLines) {
			context.drawTextWithShadow(text, line, x, y + 2, PROBLEM);
			y += LINE;
		}
	}

	/** A step's label, wrapped to the panel; its mark (a tick, an arrow or a dot) is drawn apart, in a column of its own, so the labels line up. */
	private static List<OrderedText> wrapStep(TextRenderer text, String key, int maxInner) {
		return text.wrapLines(Text.translatable("gui.the_dream_table.capture_step." + key), maxInner - MARK_COLUMN);
	}

	private static int widest(TextRenderer text, List<OrderedText> lines) {
		int widest = 0;
		for (OrderedText line : lines) {
			widest = Math.max(widest, text.getWidth(line));
		}
		return widest;
	}

	/** A block of wrapped lines takes a row, plus a line for each one after the first; none takes nothing. */
	private static int blockHeight(List<OrderedText> lines) {
		return lines.isEmpty() ? 0 : ROW + LINE * (lines.size() - 1);
	}

	/** Draws the lines from {@code y} and returns the y below the block. */
	private static int drawBlock(DrawContext context, TextRenderer text, List<OrderedText> lines, int x, int y, int color) {
		for (int index = 0; index < lines.size(); index++) {
			context.drawTextWithShadow(text, lines.get(index), x, y + 2 + index * LINE, color);
		}
		return y + blockHeight(lines);
	}

	private static Text mark(State state) {
		return Text.translatable("gui.the_dream_table.capture_mark." + state.name().toLowerCase(Locale.ROOT));
	}

	/** The size of the box, with its block count once it is fixed and counted; null before the first corner. */
	private static Text sizeText() {
		CaptureBox box = CaptureMode.box();
		if (box == null) {
			return null;
		}
		if (!CaptureMode.selection().fixed() || CaptureMode.problem() != null) {
			return Text.translatable("gui.the_dream_table.capture_size", box.sizeX(), box.sizeY(), box.sizeZ());
		}
		if (CaptureMode.pending()) {
			return Text.translatable("gui.the_dream_table.capture_size_counting", box.sizeX(), box.sizeY(), box.sizeZ());
		}
		return Text.translatable("gui.the_dream_table.capture_size_blocks", box.sizeX(), box.sizeY(), box.sizeZ(), CaptureMode.blocks());
	}

	/** The keys of the current step as the player has them bound. */
	private static List<Chip> chips(MinecraftClient client, Step current) {
		List<Chip> chips = new ArrayList<>();
		boolean canSave = CaptureMode.problem() == null && !CaptureMode.pending();
		for (Hint hint : CaptureSteps.hints(current, canSave)) {
			Text key = switch (hint) {
				case MARK, FIX -> client.options.useKey.getBoundKeyLocalizedText();
				case HEIGHT -> Text.empty().append(DreamTableKeys.CAPTURE_UP.getBoundKeyLocalizedText()).append(" ").append(DreamTableKeys.CAPTURE_DOWN.getBoundKeyLocalizedText());
				case SHRINK -> Text.translatable("key.keyboard.left.shift");
				case NAME -> DreamTableKeys.CAPTURE_CONFIRM.getBoundKeyLocalizedText();
				case EXIT -> Text.empty().append(Text.translatable("key.keyboard.escape")).append(" / ").append(DreamTableKeys.CAPTURE.getBoundKeyLocalizedText());
			};
			chips.add(new Chip(key, Text.translatable("gui.the_dream_table.capture_action." + hint.name().toLowerCase(Locale.ROOT))));
		}
		return chips;
	}
}
