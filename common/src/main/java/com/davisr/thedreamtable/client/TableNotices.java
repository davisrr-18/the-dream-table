package com.davisr.thedreamtable.client;

import com.davisr.thedreamtable.network.TableNoticePayload;
import com.davisr.thedreamtable.network.TableNoticePayload.Severity;
import com.davisr.thedreamtable.platform.Platform;
import com.davisr.thedreamtable.screen.DreamTableScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.davisr.thedreamtable.screen.ui.Theme.*;

/**
 * The mod's notice channel: one short message centered at the top of the screen for about five seconds, replacing the
 * action bar. The HUD draws it while the game is visible and the Dream Table screen draws it on top of itself, so a
 * message is never lost behind the open table. Server messages arrive as {@link TableNoticePayload}.
 */
public final class TableNotices {
	private static final NoticeBoard BOARD = new NoticeBoard();
	private static final int TOP = 6;
	private static final int PADDING = 6;
	private static final int MAX_WIDTH = 300;
	private static final int LINE = 10;

	private TableNotices() {
	}

	public static void register() {
		Platform.clientNetwork().registerReceiver(TableNoticePayload.ID, payload -> show(payload.text(), payload.severity()));
		Platform.clientRendering().onHud(context -> {
			MinecraftClient client = MinecraftClient.getInstance();
			// While the table is open it draws the notice itself, above the screen.
			if (!client.options.hudHidden && !(client.currentScreen instanceof DreamTableScreen)) {
				draw(context, client.textRenderer, true);
			}
		});
	}

	/** Shows the text now in place of any older notice. */
	public static void show(Text text, Severity severity) {
		BOARD.show(text, severity, Util.getMeasuringTimeMs());
	}

	public static void info(Text text) {
		show(text, Severity.INFO);
	}

	public static void problem(Text text) {
		show(text, Severity.PROBLEM);
	}

	static void clear() {
		BOARD.clear();
	}

	/** Sets or removes the persistent live line shown while no ordinary notice is (see {@link BuildProgressLine}). */
	static void setProgress(@Nullable Text text) {
		BOARD.setPersistent(text, Severity.INFO);
	}

	/**
	 * The screen row just below the notice that is drawn now over the world, or 0 when none is, so other HUD lines can
	 * sit under a notice of any number of lines instead of behind it.
	 */
	public static int bottom(TextRenderer font, int screenWidth) {
		long now = Util.getMeasuringTimeMs();
		NoticeBoard.Notice notice = BOARD.current(now, true);
		if (notice == null || Math.round(notice.alpha(now) * 255.0F) <= 8) {
			return 0;
		}
		return TOP + height(font.wrapLines(notice.text(), wrapWidth(screenWidth)).size());
	}

	private static int wrapWidth(int screenWidth) {
		return Math.min(MAX_WIDTH, screenWidth - 2 * (PADDING + 8));
	}

	private static int height(int lines) {
		return lines * LINE + 2 * PADDING - 2;
	}

	/**
	 * Draws the current notice, if any, centered at the top of the scaled window. The persistent progress line is left out
	 * when {@code withProgress} is false, as over the table menu, whose Work page already shows it.
	 */
	public static void draw(DrawContext context, TextRenderer font, boolean withProgress) {
		long now = Util.getMeasuringTimeMs();
		NoticeBoard.Notice notice = BOARD.current(now, withProgress);
		if (notice == null) {
			return;
		}
		int opacity = Math.round(notice.alpha(now) * 255.0F);
		if (opacity <= 8) {
			return;
		}
		int screenWidth = context.getScaledWindowWidth();
		List<OrderedText> lines = font.wrapLines(notice.text(), wrapWidth(screenWidth));
		int textWidth = 0;
		for (OrderedText line : lines) {
			textWidth = Math.max(textWidth, font.getWidth(line));
		}
		int width = textWidth + 2 * PADDING;
		int height = height(lines.size());
		int x = (screenWidth - width) / 2;
		boolean problem = notice.severity() == Severity.PROBLEM;
		context.getMatrices().push();
		context.getMatrices().translate(0.0F, 0.0F, 500.0F);
		// Translucent rounded panel; the text stays fully opaque (until the fade) so it reads on any background.
		int fill = NoticeShape.withAlpha(0x101820, opacity, NoticeShape.FILL_OPACITY);
		for (NoticeShape.Rect rect : NoticeShape.fill(x, TOP, width, height)) {
			context.fill(rect.x1(), rect.y1(), rect.x2(), rect.y2(), fill);
		}
		// The outline carries the severity as well as the color.
		int edge = NoticeShape.withAlpha(problem ? NOTICE_PROBLEM_EDGE : NOTICE_INFO_EDGE, opacity, NoticeShape.EDGE_OPACITY);
		for (NoticeShape.Rect rect : NoticeShape.outline(x, TOP, width, height)) {
			context.fill(rect.x1(), rect.y1(), rect.x2(), rect.y2(), edge);
		}
		int color = withOpacity(problem ? NOTICE_PROBLEM : NOTICE_INFO, opacity);
		int y = TOP + PADDING - 1;
		for (OrderedText line : lines) {
			context.drawTextWithShadow(font, line, (screenWidth - font.getWidth(line)) / 2, y, color);
			y += LINE;
		}
		context.getMatrices().pop();
	}

	static int withOpacity(int rgb, int opacity) {
		return (Math.max(0, Math.min(255, opacity)) << 24) | (rgb & 0xFFFFFF);
	}
}
