package com.davisr.thedreamtable.screen.page;

import com.davisr.thedreamtable.client.DreamTableHologram;
import com.davisr.thedreamtable.farm.FarmTemplate;
import com.davisr.thedreamtable.farm.FarmTemplates;
import com.davisr.thedreamtable.screen.DreamTableScreenHandler;

/** Client-side reading of the synced build phase. Phases: 0 idle, 1 building, 2 blocked, 3 done, 4 waiting for a block, 5 paused. */
public final class ProjectState {
	public static final int IDLE = 0;
	public static final int BUILDING = 1;
	public static final int BLOCKED = 2;
	public static final int DONE = 3;
	public static final int WAITING = 4;
	public static final int PAUSED = 5;

	private ProjectState() {
	}

	public static boolean inProgress(DreamTableScreenHandler handler) {
		int phase = handler.getPhase();
		return phase == BUILDING || resumable(handler);
	}

	public static boolean resumable(DreamTableScreenHandler handler) {
		int phase = handler.getPhase();
		return phase == BLOCKED || phase == WAITING || phase == PAUSED;
	}

	/** True while the current farm has materials, a placement, or a build, so no other farm may replace it. */
	public static boolean locked(DreamTableScreenHandler handler) {
		if (handler.getSelectedFarm().isEmpty() || handler.getPhase() == DONE) {
			return false;
		}
		if (inProgress(handler) || DreamTableHologram.hasPlacement()) {
			return true;
		}
		FarmTemplate template = FarmTemplates.get(handler.getSelectedFarm());
		if (template == null) {
			return false;
		}
		for (int index = 0; index < template.materials().size(); index++) {
			if (handler.supplied(index) > 0) {
				return true;
			}
		}
		return false;
	}

	public static boolean hasAllMaterials(DreamTableScreenHandler handler) {
		FarmTemplate template = FarmTemplates.get(handler.getSelectedFarm());
		if (template == null) {
			return false;
		}
		for (int index = 0; index < template.materials().size(); index++) {
			if (handler.deposited(index) < template.materials().get(index).count()) {
				return false;
			}
		}
		return true;
	}

	/** The main work button starts, pauses, or resumes; it is off only once the farm is complete. */
	public static boolean canBuild(DreamTableScreenHandler handler) {
		int phase = handler.getPhase();
		if (phase == BUILDING || resumable(handler)) {
			return true;
		}
		return phase == IDLE && FarmTemplates.get(handler.getSelectedFarm()) != null;
	}
}
