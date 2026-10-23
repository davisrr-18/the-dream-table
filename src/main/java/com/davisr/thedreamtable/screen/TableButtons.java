package com.davisr.thedreamtable.screen;

import com.davisr.thedreamtable.block.entity.DreamTableBlockEntity;
import com.davisr.thedreamtable.block.entity.TableSession;
import com.davisr.thedreamtable.farm.BuildPace;
import com.davisr.thedreamtable.farm.FarmOptions;
import com.davisr.thedreamtable.farm.FarmTemplates;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;

import static com.davisr.thedreamtable.screen.DreamTableScreenHandler.*;

/** Server side of every table button. Each press changes only the table; the handler copies it afterwards. */
final class TableButtons {
	private TableButtons() {
	}

	static boolean press(TableSession table, ServerPlayerEntity player, int id) {
		if (id >= 0 && id < FarmOptions.ALL.size()) {
			return openFarm(table, FarmOptions.ALL.get(id).id());
		}
		if (id >= BUTTON_SWAP_PICK) {
			return pickSwap(table, id - BUTTON_SWAP_PICK);
		}
		if (id >= BUTTON_SWAP_OPEN) {
			return openSwap(table, id - BUTTON_SWAP_OPEN);
		}
		if (id >= BUTTON_SCHEME) {
			List<FarmOptions.FarmOption> imported = FarmTemplates.imported();
			int index = id - BUTTON_SCHEME;
			return index < imported.size() && openFarm(table, imported.get(index).id());
		}
		return switch (id) {
			case BUTTON_MATERIALS, BUTTON_SWAP_BACK -> page(table, PAGE_MATERIALS);
			case BUTTON_MATERIALS_BACK, BUTTON_BACK -> page(table, PAGE_WORK);
			case BUTTON_MENU, BUTTON_SCHEMES_BACK, BUTTON_OPTIONS_BACK -> page(table, PAGE_SELECT);
			case BUTTON_SCHEMES -> page(table, PAGE_SCHEMES);
			case BUTTON_OPTIONS -> page(table, PAGE_OPTIONS);
			case BUTTON_REQUIREMENTS -> {
				table.setRequirementsBoard(true);
				yield true;
			}
			case BUTTON_PACE_MINUTES + 1, BUTTON_PACE_MINUTES + 5, BUTTON_PACE_MINUTES + 10 -> {
				table.setPace(BuildPace.ofMinutes(id - BUTTON_PACE_MINUTES));
				yield true;
			}
			case BUTTON_BUILD -> build(table, player);
			case BUTTON_PAUSE -> table.pause();
			case BUTTON_DISCARD -> {
				table.discard(player);
				yield true;
			}
			default -> false;
		};
	}

	private static boolean page(TableSession table, int page) {
		table.setPage(page);
		return true;
	}

	private static boolean build(TableSession table, ServerPlayerEntity player) {
		if (table.canResume()) {
			return table.begin(player, false);
		}
		if (DreamTableBlockEntity.PHASE_IDLE.equals(table.getPhase()) && !table.getSelectedFarm().isEmpty()) {
			return table.begin(player, true);
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
