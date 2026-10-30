package com.davisr.thedreamtable.screen.page;

import com.davisr.thedreamtable.farm.FarmInfo;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

import static com.davisr.thedreamtable.screen.ui.Theme.*;

/**
 * The lines of a farm's help page: each section's heading in the accent color, then its bullets wrapped to the card
 * width, the continuation lines lined up under the first word. Pure given a wrapper, so it is tested without a font.
 */
final class FarmInfoText {
	static final int LINE_HEIGHT = 10;
	/** Space above every heading but the first. */
	static final int SECTION_GAP = 8;
	/** Room the bullet mark takes left of the text. */
	static final int BULLET_INDENT = 8;

	/** One drawn line: where it starts under the top, how far it is indented, and whether it carries the bullet mark. */
	record Row(OrderedText text, int top, int indent, int color, boolean bullet) {
	}

	record Layout(List<Row> rows, int height) {
		static final Layout EMPTY = new Layout(List.of(), 0);
	}

	private FarmInfoText() {
	}

	/** {@code wrap} cuts a text into lines no wider than the given width. */
	static Layout layout(List<FarmInfo.Block> blocks, int width, BiFunction<Text, Integer, List<OrderedText>> wrap) {
		List<Row> rows = new ArrayList<>();
		int top = 0;
		for (FarmInfo.Block block : blocks) {
			if (!rows.isEmpty()) {
				top += SECTION_GAP;
			}
			for (OrderedText heading : wrap.apply(Text.translatable(block.section().titleKey()), width)) {
				rows.add(new Row(heading, top, 0, ACCENT, false));
				top += LINE_HEIGHT;
			}
			for (String bullet : block.bullets()) {
				boolean first = true;
				for (OrderedText line : wrap.apply(Text.literal(bullet), width - BULLET_INDENT)) {
					rows.add(new Row(line, top, BULLET_INDENT, NAME, first));
					first = false;
					top += LINE_HEIGHT;
				}
			}
		}
		return new Layout(rows, top);
	}
}
