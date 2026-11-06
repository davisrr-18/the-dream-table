package com.davisr.thedreamtable.client.clipboard;

import com.davisr.thedreamtable.client.clipboard.ClipboardLayout.Rows;
import com.davisr.thedreamtable.client.clipboard.ClipboardLayout.Zone;
import com.davisr.thedreamtable.client.clipboard.ClipboardLines.Line;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

import static com.davisr.thedreamtable.client.clipboard.ClipboardDraw.*;
import static com.davisr.thedreamtable.client.clipboard.ClipboardLayout.*;

/** Draws the paper: title, the Totals and Order columns, both progress bars and the page buttons. */
public final class ClipboardSheet {
	private ClipboardSheet() {
	}

	/** One column page as it is shown: its rows, its x span, and its page position. */
	public record Column(List<Line> rows, float left, float right, int page, int pages) {
		boolean canGoBack() {
			return page > 0;
		}

		boolean canGoOn() {
			return page < pages - 1;
		}
	}

	/** Everything the sheet needs for one frame. */
	public record State(Column totals, Column order, int itemHave, int itemNeed, int built, int steps, ItemStack held, double lookX, double lookY, boolean looking) {
	}

	public static void draw(MatrixStack matrices, TextRenderer text, Rows rows, State state) {
		Zone hover = state.looking() ? zoneAt(rows, state.lookX(), state.lookY(), state.totals().pages(), state.order().pages()) : Zone.NONE;
		drawChrome(matrices, text, rows, state, hover);
		drawArrows(matrices, rows, state);

		List<float[]> strikes = new ArrayList<>();
		drawIcons(matrices, text, state.totals(), strikes);
		drawIcons(matrices, text, state.order(), strikes);
		restoreWorldLighting();

		drawLabels(matrices, text, rows, state);
		drawNames(matrices, text, state.totals());
		drawNames(matrices, text, state.order());
		drawStrikes(matrices, strikes);
	}

	/** 1.20.1 takes the camera rotation that the world render passed to enableForLevel, rebuilt as vanilla builds it. */
	private static void restoreWorldLighting() {
		Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
		DiffuseLighting.enableForLevel(new Matrix4f()
				.rotate(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()))
				.rotate(RotationAxis.POSITIVE_Y.rotationDegrees(camera.getYaw() + 180.0F)));
	}

	private static void drawChrome(MatrixStack matrices, TextRenderer text, Rows rows, State state, Zone hover) {
		BufferBuilder chrome = quads();
		float titleBottom = TITLE_Y - text.fontHeight * TITLE_TEXT;
		flat(chrome, matrices, LEFT, titleBottom - 0.02F, RIGHT, titleBottom - 0.008F, 0.012F, 0.45F, 0.32F, 0.18F);
		flat(chrome, matrices, -0.012F, rows.itemLabelY() + 0.08F, 0.012F, COLUMN_Y + 0.04F, 0.012F, 0.55F, 0.40F, 0.22F);
		flat(chrome, matrices, LEFT, rows.itemLabelY() + 0.055F, RIGHT, rows.itemLabelY() + 0.070F, 0.012F, 0.45F, 0.32F, 0.18F);
		highlight(chrome, matrices, text, state.totals(), state);
		highlight(chrome, matrices, text, state.order(), state);
		progress(chrome, matrices, rows.itemBarTop(), ratio(state.itemHave(), state.itemNeed()), 0.36F, 0.64F, 0.24F);
		progress(chrome, matrices, rows.buildBarTop(), ratio(state.built(), state.steps()), 0.72F, 0.44F, 0.16F);
		plates(chrome, matrices, rows, state.totals(), hover == Zone.TOTALS_PREV, hover == Zone.TOTALS_NEXT);
		plates(chrome, matrices, rows, state.order(), hover == Zone.ORDER_PREV, hover == Zone.ORDER_NEXT);
		flush(chrome);
	}

	private static void plates(BufferBuilder buffer, MatrixStack matrices, Rows rows, Column column, boolean hoverBack, boolean hoverOn) {
		if (column.pages() <= 1) {
			return;
		}
		plate(buffer, matrices, column.left(), rows.buttonY0(), column.left() + BUTTON_W, rows.buttonY1(), column.canGoBack(), hoverBack);
		plate(buffer, matrices, column.right() - BUTTON_W, rows.buttonY0(), column.right(), rows.buttonY1(), column.canGoOn(), hoverOn);
	}

	private static void drawArrows(MatrixStack matrices, Rows rows, State state) {
		if (state.totals().pages() <= 1 && state.order().pages() <= 1) {
			return;
		}
		BufferBuilder arrows = triangles();
		for (Column column : List.of(state.totals(), state.order())) {
			if (column.pages() > 1) {
				arrow(arrows, matrices, column.left(), rows.buttonY0(), column.left() + BUTTON_W, rows.buttonY1(), false, column.canGoBack());
				arrow(arrows, matrices, column.right() - BUTTON_W, rows.buttonY0(), column.right(), rows.buttonY1(), true, column.canGoOn());
			}
		}
		flush(arrows);
	}

	private static void highlight(BufferBuilder chrome, MatrixStack matrices, TextRenderer text, Column column, State state) {
		float body = text.fontHeight * TEXT;
		int hoverRow = state.looking() ? rowAt(text, state.lookX(), state.lookY(), column.rows().size(), column.left(), column.right()) : -1;
		float row = ROW_TOP;
		for (int index = 0; index < column.rows().size(); index++) {
			boolean matchesHeld = !state.held().isEmpty() && state.held().isOf(column.rows().get(index).stack().getItem());
			if (matchesHeld || index == hoverRow) {
				float top = row + 0.03F;
				float bottom = row - Math.max(body, ICON) - 0.02F;
				if (matchesHeld) {
					flat(chrome, matrices, column.left() - 0.02F, bottom, column.right() + 0.02F, top, 0.01F, 0.72F, 0.84F, 0.52F);
				} else {
					flat(chrome, matrices, column.left() - 0.02F, bottom, column.right() + 0.02F, top, 0.01F, 0.86F, 0.78F, 0.58F);
				}
			}
			row -= ROW_STEP;
		}
	}

	/** Draws each row icon and records a strike line, as {y, from, to}, for every finished name line. */
	private static void drawIcons(MatrixStack matrices, TextRenderer text, Column column, List<float[]> strikes) {
		float row = ROW_TOP;
		for (Line line : column.rows()) {
			ClipboardNames.Placed name = ClipboardNames.place(text, line.stack().getName().getString(), line.count(), column.left(), column.right());
			item(matrices, line.stack(), column.left(), row - name.height() * 0.5F - ICON * 0.5F, ICON);
			if (line.done()) {
				float lineHeight = name.lineHeight(text);
				for (int index = 0; index < name.lines().size(); index++) {
					float mid = row - index * (lineHeight + name.gap()) - lineHeight * 0.55F;
					strikes.add(new float[]{mid, column.left() + ICON + 0.05F, column.right()});
				}
			}
			row -= ROW_STEP;
		}
	}

	private static void drawNames(MatrixStack matrices, TextRenderer text, Column column) {
		float row = ROW_TOP;
		float nameX = column.left() + ICON + 0.06F;
		for (Line line : column.rows()) {
			ClipboardNames.Placed name = ClipboardNames.place(text, line.stack().getName().getString(), line.count(), column.left(), column.right());
			float lineHeight = name.lineHeight(text);
			for (int index = 0; index < name.lines().size(); index++) {
				label(matrices, text, nameX, row - index * (lineHeight + name.gap()), Text.literal(name.lines().get(index)), name.scale());
			}
			labelRight(matrices, text, column.right(), row, Text.literal(line.count()), TEXT);
			row -= ROW_STEP;
		}
	}

	private static void drawLabels(MatrixStack matrices, TextRenderer text, Rows rows, State state) {
		labelCenter(matrices, text, 0.0F, TITLE_Y, Text.translatable("gui.the_dream_table.requirements"), TITLE_TEXT);
		label(matrices, text, TOTALS_LEFT, COLUMN_Y, Text.translatable("gui.the_dream_table.totals"), TEXT);
		label(matrices, text, ORDER_LEFT, COLUMN_Y, Text.translatable("gui.the_dream_table.order"), TEXT);
		label(matrices, text, LEFT, rows.itemLabelY(), Text.translatable("gui.the_dream_table.items"), TEXT);
		labelRight(matrices, text, RIGHT, rows.itemLabelY(), Text.literal(state.itemHave() + "/" + state.itemNeed()), TEXT);
		label(matrices, text, LEFT, rows.buildLabelY(), Text.translatable("gui.the_dream_table.blocks"), TEXT);
		labelRight(matrices, text, RIGHT, rows.buildLabelY(), Text.literal(state.built() + "/" + state.steps()), TEXT);
		float pageY = rows.buttonY1() - (BUTTON_H - text.fontHeight * PAGE_TEXT) * 0.5F;
		for (Column column : List.of(state.totals(), state.order())) {
			if (column.pages() > 1) {
				Text count = Text.translatable("gui.the_dream_table.count", column.page() + 1, column.pages());
				labelCenter(matrices, text, (column.left() + column.right()) * 0.5F, pageY, count, PAGE_TEXT);
			}
		}
	}

	private static void drawStrikes(MatrixStack matrices, List<float[]> strikes) {
		if (strikes.isEmpty()) {
			return;
		}
		BufferBuilder buffer = quads();
		float thick = 0.007F;
		for (float[] strike : strikes) {
			flat(buffer, matrices, strike[1], strike[0] - thick, strike[2], strike[0] + thick, 0.055F, 0.29F, 0.18F, 0.08F);
		}
		flush(buffer);
	}

	private static float ratio(int done, int total) {
		return total == 0 ? 0.0F : done / (float) total;
	}
}
