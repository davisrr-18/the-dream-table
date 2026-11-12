package com.davisr.thedreamtable.screen.ui;

import net.minecraft.client.gui.DrawContext;
import com.davisr.thedreamtable.screen.ui.layout.ScreenGeometry;
import com.davisr.thedreamtable.screen.ui.layout.UiRect;

import static com.davisr.thedreamtable.screen.ui.Theme.*;

import java.util.function.IntSupplier;

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
	private ScreenGeometry geometry;

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
		geometry = null;
	}

	/** Uses the adaptive screen geometry while keeping scrolling isolated to page content. */
	public void layout(ScreenGeometry geometry, int page, int contentHeight) {
		installGeometry(geometry, page);
		this.contentHeight = contentHeight;
		clampScroll();
	}

	/** Installs the new geometry before the page calculates its responsive content height. */
	public void layout(ScreenGeometry geometry, int page, IntSupplier contentHeight) {
		installGeometry(geometry, page);
		this.contentHeight = contentHeight.getAsInt();
		clampScroll();
	}

	private void installGeometry(ScreenGeometry geometry, int page) {
		if (page != scrolledPage) {
			scroll = 0;
			scrolledPage = page;
			dragging = false;
		}
		this.geometry = geometry;
		this.screenHeight = geometry.panel().height() + ScreenGeometry.SCREEN_MARGIN * 2;
		this.searchSpace = geometry.toolbar().height();
		this.originX = geometry.panel().x();
		this.originY = geometry.panel().y();
	}

	private void clampScroll() {
		scroll = Math.max(0, Math.min(maxScroll(), scroll));
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
		return geometry == null
				? Math.min(HEADER + DESIGN_VIEW, Math.max(1, screenHeight - 8))
				: geometry.panel().height();
	}

	public int panelY() {
		return geometry == null ? originY - scroll : originY;
	}

	public int cardX() {
		return geometry == null ? originX + CARD_X : geometry.contentOrigin().x();
	}

	/** Left edge of the collection area: one card in compact mode and both grid columns in wide mode. */
	public int collectionX() {
		return cardX();
	}

	/** Width available to a collection toolbar without entering the scrollbar gutter. */
	public int collectionWidth() {
		return geometry == null ? CARD_WIDTH : geometry.columns().getLast().right() - collectionX();
	}

	/** X for a single-column page: it starts at the collection edge and spans {@link #flowWidth()}. */
	public int flowX() {
		return collectionX();
	}

	/** Width of a single-column page's cards, buttons and text: the whole collection area. */
	public int flowWidth() {
		return collectionWidth();
	}

	public int collectionCenterX() {
		return collectionX() + collectionWidth() / 2;
	}

	public int flowCenterX() {
		return flowX() + flowWidth() / 2;
	}

	public int slotOriginX() {
		return geometry == null ? originX : geometry.slotOrigin().x();
	}

	public int slotOriginY() {
		return geometry == null ? panelY() : geometry.slotOrigin().y() - scroll;
	}

	public int cardY(int index) {
		return contentTop() + PAD - scroll + index * (CARD_HEIGHT + CARD_GAP);
	}

	/** Cards per row: one on compact screens, two on wide ones. */
	public int columnCount() {
		return geometry == null ? 1 : geometry.columns().size();
	}

	public int gridCardX(int index) {
		if (geometry == null) {
			return cardX();
		}
		return geometry.columns().get(Math.floorMod(index, geometry.columns().size())).x();
	}

	public int gridCardY(int index) {
		int columns = geometry == null ? 1 : geometry.columns().size();
		return cardY(index / columns);
	}

	public int gridContentHeight(int count) {
		int columns = geometry == null ? 1 : geometry.columns().size();
		int rows = (Math.max(0, count) + columns - 1) / columns;
		return PAD + listHeight(rows) + 6;
	}

	public int listBackY(int count) {
		return contentTop() + PAD - scroll + listHeight(count) + 10;
	}

	public boolean cardVisible(int y) {
		return y + CARD_HEIGHT >= contentTop() && y <= contentBottom();
	}

	public int contentLeft() {
		return geometry == null ? originX + 2 : geometry.viewport().x();
	}

	public int contentRight() {
		return geometry == null ? originX + PANEL_WIDTH - 17 : geometry.viewport().right() - 12;
	}

	public int contentTop() {
		return geometry == null ? originY + HEADER + searchSpace : geometry.viewport().y();
	}

	/** Content stops above the panel's inner border so nothing paints over the bottom edge. */
	public static final int BOTTOM_INSET = 6;

	public int contentBottom() {
		return geometry == null ? originY + visibleHeight() - 2 : geometry.viewport().bottom() - BOTTOM_INSET;
	}

	public int scroll() {
		return scroll;
	}

	/** Scrolls just enough to make the supplied screen-space control bounds visible. */
	public boolean reveal(UiRect bounds) {
		int delta = 0;
		if (bounds.y() < contentTop()) {
			delta = bounds.y() - contentTop();
		} else if (bounds.bottom() > contentBottom()) {
			delta = bounds.bottom() - contentBottom();
		}
		int next = Math.max(0, Math.min(maxScroll(), scroll + delta));
		if (next == scroll) {
			return false;
		}
		scroll = next;
		return true;
	}

	/** Slots are safe only when the full 16x16 bounds are both visible and in the fixed slot region. */
	public boolean fullyVisible(UiRect bounds) {
		return visible(bounds) && (geometry == null || geometry.slotRegion().contains(bounds));
	}

	public boolean visible(UiRect bounds) {
		UiRect visible = new UiRect(contentLeft(), contentTop(), contentRight() - contentLeft(), contentBottom() - contentTop());
		return visible.contains(bounds);
	}

	public boolean inContent(double mouseX, double mouseY) {
		return mouseX >= contentLeft() && mouseX < contentRight() && mouseY >= contentTop() && mouseY < contentBottom();
	}

	public boolean hit(double mouseX, double mouseY, int x, int y, int width, int height) {
		return inContent(mouseX, mouseY) && inside(mouseX, mouseY, x, y, width, height);
	}

	public boolean outsidePanel(double mouseX, double mouseY) {
		int width = geometry == null ? PANEL_WIDTH : geometry.panel().width();
		return mouseX < originX || mouseY < originY || mouseX >= originX + width || mouseY >= originY + visibleHeight();
	}

	/** Whether the content area has any size; when it has none, nothing may be drawn into it. */
	public boolean hasContentArea() {
		return contentRight() > contentLeft() && contentBottom() > contentTop();
	}

	/** Clips drawing to the content area and returns true, or returns false, clipping nothing, when there is no area. */
	public boolean clip(DrawContext context) {
		if (!hasContentArea()) {
			return false;
		}
		context.enableScissor(contentLeft(), contentTop(), contentRight(), contentBottom());
		clipped = true;
		return true;
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
		return Math.max(1, contentBottom() - contentTop());
	}

	private int trackX() {
		return contentRight() + 2;
	}

	/** The track starts under the tabs on every page, also alongside a toolbar, which never reaches the gutter. */
	private int trackY() {
		return (geometry == null ? contentTop() : geometry.toolbar().y()) + PAD;
	}

	private int trackHeight() {
		return Math.max(12, contentBottom() - trackY() - 10);
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
