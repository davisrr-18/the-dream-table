package com.davisr.thedreamtable.client.map;

import com.davisr.thedreamtable.client.map.CapturePanelLayout.SizeLine;
import com.davisr.thedreamtable.farm.capture.CaptureBox;
import com.davisr.thedreamtable.farm.capture.CaptureProblem;
import com.davisr.thedreamtable.farm.capture.CaptureSteps;
import com.davisr.thedreamtable.farm.capture.CaptureSteps.State;
import com.davisr.thedreamtable.farm.capture.CaptureSteps.Step;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Language;

import java.util.List;
import java.util.Locale;

import static com.davisr.thedreamtable.client.map.CapturePanelLayout.INNER_WIDTH;
import static com.davisr.thedreamtable.client.map.CapturePanelLayout.LINE;
import static com.davisr.thedreamtable.client.map.CapturePanelLayout.MARGIN;
import static com.davisr.thedreamtable.client.map.CapturePanelLayout.PADDING;
import static com.davisr.thedreamtable.client.map.CapturePanelLayout.ROW;

/**
 * The capture mode's panel at the top left, in the look of map mode's controls bar ({@link KeyChips}): the three steps with the
 * finished ones ticked and the current one highlighted, the size, and a slot for what is wrong. Its size and every line's place
 * are fixed ({@link CapturePanelLayout}), so nothing moves while the box is counted; the keys of the current step are in the
 * controls bar at the bottom, as in the placement map.
 */
final class CapturePanel {
	/** The width of the column the step marks sit in. */
	private static final int MARK_COLUMN = 12;
	private static final int TEXT = 0xFFFFFFFF;
	private static final int PENDING = 0xFF8A8A8A;
	private static final int DONE_MARK = 0xFF7CD67C;
	private static final int DONE_TEXT = 0xFFD8D8D8;
	private static final int SIZE = 0xFFFFE066;
	private static final int PROBLEM = 0xFFFF7777;
	private static final int CURRENT_FILL = 0x558CD8FF;

	private CapturePanel() {
	}

	static void draw(DrawContext context, TextRenderer text) {
		Step current = MapCapture.step();
		CaptureBox box = MapCapture.box();
		CaptureProblem problem = MapCapture.problem();
		context.fill(MARGIN, MARGIN, MARGIN + CapturePanelLayout.width(), MARGIN + CapturePanelLayout.height(), KeyChips.PANEL_COLOR);

		int x = MARGIN + PADDING;
		int y = MARGIN + PADDING;
		line(context, text, Text.translatable("gui.the_dream_table.capture_title"), x, y, INNER_WIDTH, KeyChips.KEY_BORDER);
		y += ROW;
		for (int index = 0; index < CapturePanelLayout.STEP_ROWS; index++) {
			State state = CaptureSteps.stateOf(Step.values()[index], current);
			if (state == State.CURRENT) {
				context.fill(x - 3, y - 1, x + INNER_WIDTH + 3, y + 11, CURRENT_FILL);
			}
			context.drawTextWithShadow(text, Text.translatable("gui.the_dream_table.capture_mark." + state.name().toLowerCase(Locale.ROOT)), x, y + 2,
					state == State.DONE ? DONE_MARK : state == State.CURRENT ? TEXT : PENDING);
			line(context, text, Text.translatable("gui.the_dream_table.capture_step." + STEP_KEYS[index]), x + MARK_COLUMN, y, INNER_WIDTH - MARK_COLUMN,
					state == State.CURRENT ? TEXT : state == State.DONE ? DONE_TEXT : PENDING);
			y += ROW;
		}
		Text size = sizeText(box, CapturePanelLayout.sizeLine(box != null, MapCapture.selection().fixed(), MapCapture.pending(), problem != null));
		if (size != null) {
			line(context, text, size, x, y, INNER_WIDTH, SIZE);
		}
		y += ROW;
		if (problem != null) {
			List<StringVisitable> wrapped = text.getTextHandler().wrapLines(problem.text(), INNER_WIDTH, Style.EMPTY);
			List<StringVisitable> lines = CapturePanelLayout.problemSlot(wrapped);
			for (int index = 0; index < lines.size(); index++) {
				StringVisitable shown = lines.get(index);
				if (index == lines.size() - 1 && CapturePanelLayout.problemCut(wrapped.size())) {
					// A problem longer than its slot ends with an ellipsis instead of stopping mid-sentence.
					shown = StringVisitable.concat(text.trimToWidth(shown, INNER_WIDTH - text.getWidth(ELLIPSIS)), StringVisitable.plain(ELLIPSIS));
				}
				context.drawTextWithShadow(text, Language.getInstance().reorder(shown), x, y + 2 + index * LINE, PROBLEM);
			}
		}
	}

	private static final String ELLIPSIS = "…";

	private static final String[] STEP_KEYS = {"first", "second", "adjust"};

	/** One line, cut to its width when it is longer, so it never takes a second row. */
	private static void line(DrawContext context, TextRenderer text, Text message, int x, int y, int width, int color) {
		StringVisitable fitted = text.getWidth(message) <= width ? message : text.trimToWidth(message, width);
		context.drawTextWithShadow(text, Language.getInstance().reorder(fitted), x, y + 2, color);
	}

	/** The size line's text; its width never matters, because the panel's does not follow it. */
	private static Text sizeText(CaptureBox box, SizeLine line) {
		return switch (line) {
			case NONE -> null;
			case SIZE -> Text.translatable("gui.the_dream_table.capture_size", box.sizeX(), box.sizeY(), box.sizeZ());
			case COUNTING -> Text.translatable("gui.the_dream_table.capture_size_counting", box.sizeX(), box.sizeY(), box.sizeZ());
			case BLOCKS -> Text.translatable("gui.the_dream_table.capture_size_blocks", box.sizeX(), box.sizeY(), box.sizeZ(), MapCapture.blocks());
		};
	}
}
