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
				draw(context, client.textRenderer);
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

	/** Draws the current notice, if any, centered at the top of the scaled window. */
	public static void draw(DrawContext context, TextRenderer font) {
		long now = Util.getMeasuringTimeMs();
		NoticeBoard.Notice notice = BOARD.current(now);
		if (notice == null) {
			return;
		}
		int opacity = Math.round(NoticeBoard.alpha(now - notice.shownAt()) * 255.0F);
		if (opacity <= 8) {
			return;
		}
		int screenWidth = context.getScaledWindowWidth();
		List<OrderedText> lines = font.wrapLines(notice.text(), Math.min(MAX_WIDTH, screenWidth - 2 * (PADDING + 8)));
		int textWidth = 0;
		for (OrderedText line : lines) {
			textWidth = Math.max(textWidth, font.getWidth(line));
		}
		int width = textWidth + 2 * PADDING;
		int height = lines.size() * LINE + 2 * PADDING - 2;
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
