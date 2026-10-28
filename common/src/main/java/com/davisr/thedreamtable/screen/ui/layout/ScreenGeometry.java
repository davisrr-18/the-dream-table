package com.davisr.thedreamtable.screen.ui.layout;

import com.davisr.thedreamtable.screen.DreamTableLayout;

import java.util.List;

public record ScreenGeometry(
		LayoutMode mode,
		UiRect panel,
		UiRect header,
		UiRect tabs,
		UiRect toolbar,
		UiRect viewport,
		UiPoint contentOrigin,
		List<UiRect> columns,
		UiRect slotRegion,
		UiPoint slotOrigin
) {
	public static final int SCREEN_MARGIN = 4;
	/** Fixed panel height in GUI pixels, so the panel only scales with the GUI scale; shorter screens cap it. */
	public static final int PANEL_HEIGHT = 256;
	public static final int HEADER_HEIGHT = 38;
	public static final int TABS_HEIGHT = 30;
	public static final int CONTENT_INSET = 14;
	public static final int COLUMN_WIDTH = 168;
	public static final int COLUMN_GAP = 20;
	public static final int SLOT_REGION_WIDTH = 196;

	public ScreenGeometry {
		columns = List.copyOf(columns);
	}

	public static ScreenGeometry calculate(int screenWidth, int screenHeight, int toolbarHeight, boolean slotPage) {
		LayoutMode mode = LayoutMode.forWidth(screenWidth);
		int panelWidth = mode.panelWidth();
		int panelHeight = Math.max(0, Math.min(PANEL_HEIGHT, screenHeight - SCREEN_MARGIN * 2));
		int panelX = (screenWidth - panelWidth) / 2;
		int panelY = (screenHeight - panelHeight) / 2;
		UiRect panel = new UiRect(panelX, panelY, panelWidth, panelHeight);

		int headerHeight = Math.min(HEADER_HEIGHT, panelHeight);
		UiRect header = new UiRect(panelX, panelY, panelWidth, headerHeight);
		int tabsHeight = Math.min(TABS_HEIGHT, panelHeight - headerHeight);
		UiRect tabs = new UiRect(panelX, header.bottom(), panelWidth, tabsHeight);
		int boundedToolbarHeight = Math.min(Math.max(0, toolbarHeight), panelHeight - headerHeight - tabsHeight);
		UiRect toolbar = new UiRect(panelX, tabs.bottom(), panelWidth, boundedToolbarHeight);
		UiRect viewport = new UiRect(panelX, toolbar.bottom(), panelWidth, panel.bottom() - toolbar.bottom());

		List<UiRect> columns = mode == LayoutMode.COMPACT
				? List.of(column(panelX + CONTENT_INSET, viewport))
				: List.of(
						column(panelX + CONTENT_INSET, viewport),
						column(panelX + CONTENT_INSET + COLUMN_WIDTH + COLUMN_GAP, viewport)
				);
		UiRect slotRegion = new UiRect(panelX, viewport.y(), slotPage ? SLOT_REGION_WIDTH : 0, viewport.height());
		UiPoint slotOrigin = new UiPoint(panelX, viewport.y() - DreamTableLayout.HEADER);
		return new ScreenGeometry(mode, panel, header, tabs, toolbar, viewport,
				new UiPoint(columns.getFirst().x(), viewport.y()), columns, slotRegion, slotOrigin);
	}

	private static UiRect column(int x, UiRect viewport) {
		return new UiRect(x, viewport.y(), COLUMN_WIDTH, viewport.height());
	}
}
