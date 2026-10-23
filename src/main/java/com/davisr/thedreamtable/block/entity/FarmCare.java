package com.davisr.thedreamtable.block.entity;

import com.davisr.thedreamtable.farm.FarmAreas;
import com.davisr.thedreamtable.farm.FarmDismantler;
import com.davisr.thedreamtable.farm.FarmLoader;
import com.davisr.thedreamtable.farm.FarmSite;
import com.davisr.thedreamtable.farm.FarmTemplate;
import com.davisr.thedreamtable.farm.FarmTemplates;
import com.davisr.thedreamtable.screen.DreamTableScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

import java.util.BitSet;

/**
 * Repairing and taking down a player's finished farm from My Farms, at the table they are using.
 * A repair is a new build of the same farm at the same spot, facing and swaps: blocks still in place are skipped,
 * so only the missing ones are placed and paid for. Taking down removes every block that still matches the farm and
 * hands them back, as mining them would, a few per tick like any other take-apart.
 */
public final class FarmCare {
	private FarmCare() {
	}

	public static boolean repair(TableSession session, ServerPlayerEntity player, FarmLoader.Located farm) {
		FarmTemplate template = checked(session, player, farm);
		if (template == null) {
			return false;
		}
		FarmSite site = farm.farm().site();
		if (site.anchor() == null) {
			player.sendMessage(Text.translatable("gui.the_dream_table.repair_unsupported"), true);
			return false;
		}
		if (session.hasProgress() || session.isBusy()) {
			player.sendMessage(Text.translatable("gui.the_dream_table.repair_busy"), true);
			return false;
		}
		if (FarmLoader.missing(farm, template) == 0) {
			player.sendMessage(Text.translatable("gui.the_dream_table.repair_nothing"), true);
			return false;
		}
		ServerWorld world = farm.world();
		session.setSelectedFarm(farm.farm().farmId());
		session.restoreSwaps(farm.farm().swaps());
		session.setPlacement(site.anchor(), site.facing());
		FarmAreas areas = FarmAreas.of(world);
		areas.release(farm.farm().key());
		if (!session.build().begin(player, true)) {
			areas.recordFinished(player.getUuid(), template, site, farm.farm().swaps());
			return false;
		}
		session.setPage(DreamTableScreenHandler.PAGE_WORK);
		player.sendMessage(Text.translatable("gui.the_dream_table.repairing"), true);
		return true;
	}

	public static boolean takeDown(TableSession session, ServerPlayerEntity player, FarmLoader.Located farm) {
		FarmTemplate template = checked(session, player, farm);
		if (template == null) {
			return false;
		}
		int steps = template.steps().size();
		BitSet every = new BitSet(steps);
		every.set(0, steps);
		FarmSite site = farm.farm().site();
		FarmDismantler job = new FarmDismantler(player.getUuid(), farm.farm().farmId(), farm.farm().signature(), site,
				farm.farm().swaps(), every, (BitSet) every.clone(), steps);
		FarmAreas areas = FarmAreas.of(farm.world());
		areas.release(farm.farm().key());
		areas.claim(job.areaKey(), site.footprint(template));
		session.table().startDismantling(job);
		player.sendMessage(Text.translatable("gui.the_dream_table.taking_down"), true);
		return true;
	}

	/** The farm's template, when it is in this table's dimension and its file is unchanged; otherwise null, after telling the player. */
	private static FarmTemplate checked(TableSession session, ServerPlayerEntity player, FarmLoader.Located farm) {
		if (session.table().getWorld() != farm.world()) {
			player.sendMessage(Text.translatable("gui.the_dream_table.other_dimension"), true);
			return null;
		}
		FarmTemplate template = FarmTemplates.get(farm.farm().farmId());
		if (template == null || template.signature() != farm.farm().signature()) {
			player.sendMessage(Text.translatable("gui.the_dream_table.farm_changed"), true);
			return null;
		}
		return template;
	}
}
