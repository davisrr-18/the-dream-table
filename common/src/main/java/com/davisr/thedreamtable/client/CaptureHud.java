package com.davisr.thedreamtable.client;

import com.davisr.thedreamtable.farm.capture.CaptureBox;
import com.davisr.thedreamtable.farm.capture.CaptureProblem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** The capture's hint panel at the top left while the mode is on: what to do next, the live size, what is wrong, and the keys. */
final class CaptureHud {
	private static final int MARGIN = 8;
	private static final int PADDING = 4;
	private static final int LINE = 11;
	private static final int FILL = 0x99000000;
	private static final int TEXT = 0xFFFFFFFF;
	private static final int PROBLEM = 0xFFFF7777;
	private static final int HINT = 0xFFD8D8D8;

	private record Line(Text text, int color) {
	}

	private CaptureHud() {
	}

	static void render(DrawContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (!CaptureMode.active() || client.options.hudHidden) {
			return;
		}
		List<Line> lines = new ArrayList<>();
		lines.add(new Line(status(), TEXT));
		CaptureProblem problem = CaptureMode.problem();
		if (problem != null) {
			lines.add(new Line(problem.text(), PROBLEM));
		}
		lines.add(new Line(Text.translatable("gui.the_dream_table.capture_hint", client.options.useKey.getBoundKeyLocalizedText(),
				DreamTableKeys.CAPTURE.getBoundKeyLocalizedText()), HINT));
		TextRenderer text = client.textRenderer;
		int width = 0;
		for (Line line : lines) {
			width = Math.max(width, text.getWidth(line.text()));
		}
		context.fill(MARGIN - PADDING, MARGIN - PADDING, MARGIN + width + PADDING, MARGIN + lines.size() * LINE + PADDING - 2, FILL);
		int y = MARGIN;
		for (Line line : lines) {
			context.drawTextWithShadow(text, line.text(), MARGIN, y, line.color());
			y += LINE;
		}
	}

	private static Text status() {
		CaptureBox box = CaptureMode.box();
		if (box == null) {
			return Text.translatable("gui.the_dream_table.capture_first");
		}
		if (!CaptureMode.selection().fixed()) {
			return Text.translatable("gui.the_dream_table.capture_second", box.sizeX(), box.sizeY(), box.sizeZ());
		}
		if (CaptureMode.pending()) {
			return Text.translatable("gui.the_dream_table.capture_counting", box.sizeX(), box.sizeY(), box.sizeZ());
		}
		if (CaptureMode.problem() == null) {
			return Text.translatable("gui.the_dream_table.capture_ready", box.sizeX(), box.sizeY(), box.sizeZ(), CaptureMode.blocks());
		}
		return Text.translatable("gui.the_dream_table.capture_size", box.sizeX(), box.sizeY(), box.sizeZ());
	}
}
