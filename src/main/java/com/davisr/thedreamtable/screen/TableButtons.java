package com.davisr.thedreamtable.screen;

import com.davisr.thedreamtable.block.entity.DreamTableBlockEntity;
import com.davisr.thedreamtable.block.entity.TableSession;
import com.davisr.thedreamtable.farm.BuildPace;
import com.davisr.thedreamtable.farm.FarmOptions;
import com.davisr.thedreamtable.farm.FarmTemplates;
import com.davisr.thedreamtable.screen.page.PageRoutes;
import com.davisr.thedreamtable.screen.page.PrimaryTab;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;

import static com.davisr.thedreamtable.screen.DreamTableScreenHandler.*;

/** Server side of every table button. Each press changes only the table; the handler copies it afterwards. */
final class TableButtons {
	private TableButtons() {
	}

	static boolean press(TableSession table, ServerPlayerEntity player, int id) {
		if (id >= 0 && id < BUTTON_REQUIREMENTS && id < FarmOptions.ALL.size()) {
			return openFarm(table, FarmOptions.ALL.get(id).id());
		}
		if (id >= BUTTON_CATEGORY && id < BUTTON_CATEGORY + PageRoutes.CATEGORY_PAGE_COUNT) {
			return page(table, PAGE_CATEGORY + id - BUTTON_CATEGORY);
		}
		if (dynamicRange(id) == DynamicRange.CUSTOM_PACE) {
			table.build().setPace(BuildPace.ofPercent(id - BUTTON_PACE_CUSTOM));
			return true;
		}
		if (dynamicRange(id) == DynamicRange.SWAP_PICK) {
			return pickSwap(table, id - BUTTON_SWAP_PICK);
		}
		if (dynamicRange(id) == DynamicRange.SWAP_OPEN) {
			return openSwap(table, id - BUTTON_SWAP_OPEN);
		}
		if (dynamicRange(id) == DynamicRange.SCHEMATIC) {
			List<FarmOptions.FarmOption> imported = FarmTemplates.imported();
			int index = id - BUTTON_SCHEME;
			return index < imported.size() && openFarm(table, imported.get(index).id());
		}
		return switch (id) {
			case BUTTON_TAB_BUILD -> page(table, PageRoutes.rootFor(PrimaryTab.BUILD, !table.getSelectedFarm().isEmpty()));
			case BUTTON_TAB_FARMS -> page(table, PageRoutes.rootFor(PrimaryTab.FARMS, false));
			case BUTTON_TAB_SCHEMATICS -> page(table, PageRoutes.rootFor(PrimaryTab.SCHEMATICS, false));
			case BUTTON_TAB_SETTINGS -> page(table, PageRoutes.rootFor(PrimaryTab.SETTINGS, false));
			case BUTTON_MATERIALS, BUTTON_SWAP_BACK -> page(table, PAGE_MATERIALS);
			case BUTTON_MATERIALS_BACK, BUTTON_BACK -> page(table, PAGE_WORK);
			case BUTTON_MENU, BUTTON_SCHEMES_BACK, BUTTON_OPTIONS_BACK, BUTTON_CATEGORY_BACK -> page(table, PAGE_SELECT);
			case BUTTON_SCHEMES -> page(table, PAGE_SCHEMES);
			case BUTTON_OPTIONS, BUTTON_SPEED_BACK, BUTTON_LOADED_BACK -> page(table, PAGE_OPTIONS);
			case BUTTON_SPEED_OPEN -> page(table, PAGE_SPEED);
			case BUTTON_LOADED_FARMS, BUTTON_FARM_BACK -> page(table, PAGE_LOADED);
			case BUTTON_INVITES -> page(table, PAGE_INVITES);
			case BUTTON_ALERTS -> page(table, PAGE_ALERTS);
			case BUTTON_FARM_OPEN -> page(table, PAGE_FARM);
			case BUTTON_REQUIREMENTS -> {
				table.setRequirementsBoard(true);
				yield true;
			}
			case BUTTON_PACE, BUTTON_PACE + 1, BUTTON_PACE + 2 -> {
				table.build().setPace(BuildPace.LEVELS[id - BUTTON_PACE]);
				yield true;
			}
			case BUTTON_BUILD -> build(table, player);
			case BUTTON_PAUSE -> table.build().pause();
			case BUTTON_DISCARD -> {
				table.discard(player);
				yield true;
			}
			default -> false;
		};
	}

	static DynamicRange dynamicRange(int id) {
		if (id >= BUTTON_SCHEME && id < BUTTON_SWAP_OPEN) {
			return DynamicRange.SCHEMATIC;
		}
		if (id >= BUTTON_SWAP_OPEN && id < BUTTON_SWAP_PICK) {
			return DynamicRange.SWAP_OPEN;
		}
		if (id >= BUTTON_SWAP_PICK && id < BUTTON_PACE_CUSTOM) {
			return DynamicRange.SWAP_PICK;
		}
		if (id > BUTTON_PACE_CUSTOM && id <= BUTTON_PACE_CUSTOM + BuildPace.MAX_PERCENT) {
			return DynamicRange.CUSTOM_PACE;
		}
		return DynamicRange.NONE;
	}

	enum DynamicRange {
		NONE,
		SCHEMATIC,
		SWAP_OPEN,
		SWAP_PICK,
		CUSTOM_PACE
	}

	private static boolean page(TableSession table, int page) {
		table.setPage(page);
		return true;
	}

	private static boolean build(TableSession table, ServerPlayerEntity player) {
		if (table.build().canResume()) {
			return table.build().begin(player, false);
		}
		if (DreamTableBlockEntity.PHASE_IDLE.equals(table.getPhase()) && !table.getSelectedFarm().isEmpty()) {
			return table.build().begin(player, true);
		}
		return false;
	}

	private static boolean openSwap(TableSession table, int material) {
		if (!table.canSwap(material) || table.swapOptions(material).isEmpty()) {
			return false;
		}
		table.setSwapMaterial(material);
		return page(table, PAGE_SWAP);
	}

	private static boolean pickSwap(TableSession table, int option) {
		int material = table.getSwapMaterial();
		if (material < 0 || !table.setSwap(material, option)) {
			return false;
		}
		return page(table, PAGE_MATERIALS);
	}

	/** Opening the current farm returns to it; another farm only replaces it when nothing was started. */
	private static boolean openFarm(TableSession table, String id) {
		if (!id.equals(table.getSelectedFarm())) {
			if (table.hasProgress()) {
				return true;
			}
			table.setSelectedFarm(id);
		}
		return page(table, PAGE_WORK);
	}
}
