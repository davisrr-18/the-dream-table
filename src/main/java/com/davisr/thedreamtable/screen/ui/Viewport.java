package com.davisr.thedreamtable.screen.ui;

import net.minecraft.client.gui.DrawContext;

import static com.davisr.thedreamtable.screen.ui.Theme.*;

/** Keeps the panel fixed on screen and scrolls only the content below the header. */
public final class Viewport {
	public static final int DESIGN_VIEW = PAD + 5 * CARD_HEIGHT + 4 * CARD_GAP + 4;

	private int scroll;
	private int scrolledPage = -1;
	private boolean dragging;
	private int dragGrab;
	private int originX;
	private int originY;
	private int screenHeight;
	private int searchSpace;
	private int contentHeight;
	private boolean clipped;

	public void layout(int screenWidth, int screenHeight, int page, int searchSpace, int contentHeight) {
		if (page != scrolledPage) {
			scroll = 0;
			scrolledPage = page;
			dragging = false;
		}
		this.screenHeight = screenHeight;
		this.searchSpace = searchSpace;
		this.contentHeight = contentHeight;
		scroll = Math.max(0, Math.min(maxScroll(), scroll));
		originX = (screenWidth - PANEL_WIDTH) / 2;
		originY = Math.max(4, (screenHeight - visibleHeight()) / 2);
	}

	public void resetScroll() {
		scroll = 0;
	}

	public boolean scrollBy(double notches) {
		int max = maxScroll();
		if (max <= 0) {
			return false;
		}
		scroll = (int) Math.max(0, Math.min(max, scroll - notches * (CARD_HEIGHT + CARD_GAP)));
		return true;
	}

	public boolean beginDrag(double mouseX, double mouseY) {
		if (maxScroll() <= 0 || !inside(mouseX, mouseY, trackX() - 3, trackY(), TRACK_WIDTH + 6, trackHeight())) {
			return false;
		}
		dragging = true;
		int thumbTop = thumbY();
		dragGrab = mouseY >= thumbTop && mouseY <= thumbTop + thumbHeight() ? (int) mouseY - thumbTop : thumbHeight() / 2;
		drag(mouseY);
		return true;
	}

	public boolean drag(double mouseY) {
		if (!dragging) {
			return false;
		}
		int travel = Math.max(1, trackHeight() - thumbHeight());
		int relative = (int) mouseY - dragGrab - trackY();
		int max = maxScroll();
		scroll = Math.max(0, Math.min(max, max * relative / travel));
		return true;
	}

	public void endDrag() {
		dragging = false;
	}

	public int originX() {
		return originX;
	}

	public int originY() {
		return originY;
	}

	public int visibleHeight() {
		return Math.min(HEADER + DESIGN_VIEW, Math.max(1, screenHeight - 8));
	}

	public int panelY() {
		return originY - scroll;
	}

	public int cardX() {
		return originX + CARD_X;
	}

	public int cardY(int index) {
		return panelY() + HEADER + searchSpace + PAD + index * (CARD_HEIGHT + CARD_GAP);
	}

	public int listBackY(int count) {
		return panelY() + HEADER + searchSpace + PAD + listHeight(count) + 10;
	}

	public boolean cardVisible(int y) {
		return y + CARD_HEIGHT >= contentTop() && y <= contentBottom();
	}

	public int contentLeft() {
		return originX + 2;
	}

	public int contentRight() {
		return originX + PANEL_WIDTH - 17;
	}

	public int contentTop() {
		return originY + HEADER + searchSpace;
	}

	public int contentBottom() {
		return originY + visibleHeight() - 2;
	}

	public boolean inContent(double mouseX, double mouseY) {
		return mouseX >= contentLeft() && mouseX < contentRight() && mouseY >= contentTop() && mouseY < contentBottom();
	}

	public boolean hit(double mouseX, double mouseY, int x, int y, int width, int height) {
		return inContent(mouseX, mouseY) && inside(mouseX, mouseY, x, y, width, height);
	}

	public boolean outsidePanel(double mouseX, double mouseY) {
		return mouseX < originX || mouseY < originY || mouseX >= originX + PANEL_WIDTH || mouseY >= originY + visibleHeight();
	}

	public void clip(DrawContext context) {
		if (contentRight() <= contentLeft() || contentBottom() <= contentTop()) {
			return;
		}
		context.enableScissor(contentLeft(), contentTop(), contentRight(), contentBottom());
		clipped = true;
	}

	public void release(DrawContext context) {
		if (clipped) {
			context.disableScissor();
			clipped = false;
		}
	}

	public void drawScrollbar(DrawContext context, Painter painter, int mouseX, int mouseY) {
		if (maxScroll() <= 0) {
			return;
		}
		int x = trackX();
		int y = trackY();
		int height = trackHeight();
		int thumb = thumbHeight();
		int thumbTop = thumbY();
		boolean hovered = dragging || inside(mouseX, mouseY, x - 2, thumbTop, 12, thumb);
		context.fill(x, y, x + TRACK_WIDTH, y + height, TRACK_EDGE);
		context.fill(x + 1, y + 1, x + TRACK_WIDTH - 1, y + height - 1, TRACK_FILL);
		context.fill(x + 1, y + 1, x + 2, y + height - 1, 0xFF0F4478);

		int left = x - 1;
		int right = x + TRACK_WIDTH + 1;
		painter.ring(context, left, thumbTop, right - left, thumb, THUMB_EDGE);
		context.fill(left + 1, thumbTop + 1, right - 1, thumbTop + thumb - 1, hovered ? 0xFF4A92DC : THUMB_FILL);
		context.fill(left + 1, thumbTop + 1, right - 1, thumbTop + 2, THUMB_LIGHT);
		context.fill(left + 1, thumbTop + 1, left + 2, thumbTop + thumb - 1, THUMB_LIGHT);
		context.fill(right - 2, thumbTop + 2, right - 1, thumbTop + thumb - 1, THUMB_DARK);
		context.fill(left + 2, thumbTop + thumb - 2, right - 1, thumbTop + thumb - 1, THUMB_DARK);
		if (thumb >= 14) {
			int grip = thumbTop + thumb / 2;
			for (int line = -1; line <= 1; line++) {
				context.fill(x + 2, grip + line * 3, x + TRACK_WIDTH - 2, grip + line * 3 + 1, THUMB_EDGE);
			}
		}
	}

	public static int listHeight(int count) {
		return count <= 0 ? 0 : count * CARD_HEIGHT + (count - 1) * CARD_GAP;
	}

	public static int listContentHeight(int count) {
		return PAD + listHeight(count) + 10 + BUTTON_HEIGHT + 8;
	}

	public static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}

	private int maxScroll() {
		return Math.max(0, contentHeight - viewHeight());
	}

	private int viewHeight() {
		return Math.max(1, visibleHeight() - HEADER - searchSpace);
	}

	private int trackX() {
		return originX + PANEL_WIDTH - 15;
	}

	private int trackY() {
		return originY + HEADER + searchSpace + PAD;
	}

	private int trackHeight() {
		return Math.max(12, visibleHeight() - HEADER - searchSpace - PAD - 10);
	}

	private int thumbHeight() {
		int track = trackHeight();
		int view = viewHeight();
		int content = Math.max(view, contentHeight);
		return Math.min(track, Math.max(28, track * view / content));
	}

	private int thumbY() {
		int travel = Math.max(1, trackHeight() - thumbHeight());
		int max = maxScroll();
		return max <= 0 ? trackY() : trackY() + travel * scroll / max;
	}
}
