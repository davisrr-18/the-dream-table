package com.davisr.thedreamtable.screen.page;

import static com.davisr.thedreamtable.screen.DreamTableScreenHandler.*;

public final class PageRoutes {
	public static final int NO_PARENT = -1;
	public static final int CATEGORY_PAGE_COUNT = 3;

	private PageRoutes() {
	}

	public static PrimaryTab tabFor(int page) {
		if (category(page)) {
			return PrimaryTab.BUILD;
		}
		return switch (page) {
			case PAGE_SELECT, PAGE_WORK, PAGE_REQUIREMENTS, PAGE_MATERIALS, PAGE_SWAP, PAGE_FARM_INFO -> PrimaryTab.BUILD;
			case PAGE_SCHEMES -> PrimaryTab.SCHEMATICS;
			case PAGE_OPTIONS, PAGE_SPEED -> PrimaryTab.SETTINGS;
			case PAGE_LOADED, PAGE_FARM, PAGE_INVITES -> PrimaryTab.FARMS;
			default -> throw new IllegalArgumentException("Unregistered page: " + page);
		};
	}

	public static int rootFor(PrimaryTab tab, boolean activeWork) {
		return switch (tab) {
			case BUILD -> activeWork ? PAGE_WORK : PAGE_SELECT;
			case FARMS -> PAGE_LOADED;
			case SCHEMATICS -> PAGE_SCHEMES;
			case SETTINGS -> PAGE_OPTIONS;
		};
	}

	public static int parentOf(int page) {
		if (category(page)) {
			return PAGE_SELECT;
		}
		return switch (page) {
			case PAGE_WORK -> PAGE_SELECT;
			case PAGE_REQUIREMENTS, PAGE_MATERIALS, PAGE_FARM_INFO -> PAGE_WORK;
			case PAGE_SWAP -> PAGE_MATERIALS;
			case PAGE_SPEED -> PAGE_OPTIONS;
			case PAGE_FARM, PAGE_INVITES -> PAGE_LOADED;
			default -> NO_PARENT;
		};
	}

	public static boolean isRoot(int page) {
		return page == PAGE_SELECT || page == PAGE_LOADED || page == PAGE_SCHEMES || page == PAGE_OPTIONS;
	}

	private static boolean category(int page) {
		return page >= PAGE_CATEGORY && page < PAGE_CATEGORY + CATEGORY_PAGE_COUNT;
	}
}
