package com.davisr.thedreamtable.block.entity;

import com.davisr.thedreamtable.farm.FarmAreas;
import com.davisr.thedreamtable.farm.FarmDismantler;
import com.davisr.thedreamtable.farm.FarmKey;
import com.davisr.thedreamtable.farm.FarmLoader;
import com.davisr.thedreamtable.farm.FarmOperation;
import com.davisr.thedreamtable.farm.FarmSite;
import com.davisr.thedreamtable.farm.FarmTemplate;
import com.davisr.thedreamtable.screen.DreamTableScreenHandler;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

import java.util.BitSet;
import java.util.UUID;

/**
 * Repairing and taking down a player's finished farm from My Farms, at the table they are using.
 * Both use the design the farm was built with, saved with it, not the current one.
 * A repair is a new build of the same farm at the same spot, facing and swaps: blocks still in place are skipped,
 * so only the missing ones are placed and paid for, and a spot where the player put another block is left as it is. Taking down removes the blocks the table
 * placed that still match the farm and hands back the ones it was paid for, a few per tick like any other take-apart.
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
		FarmAreas areas = FarmAreas.of(world);
		UUID operationId = UUID.randomUUID();
		// The lease comes first: a refused Repair must leave the player's current selection and placement alone.
		if (areas.beginRepair(farm.farm().instanceId(), player.getUuid(), session.getPos(), operationId).isEmpty()) {
			player.sendMessage(Text.translatable("gui.the_dream_table.repair_busy"), true);
			return false;
		}
		session.setSelectedFarm(template.id(), player);
		session.restoreSwaps(farm.farm().swaps());
		session.setPlacement(site.anchor(), site.facing());
		RepairContext context = new RepairContext(FarmKey.of(world, farm.farm().instanceId()), farm.farm().ownerUuid(), player.getUuid(), operationId);
		if (!session.build().beginRepair(player, context)) {
			areas.failRepair(farm.farm().instanceId(), operationId);
			return false;
		}
		session.setPage(DreamTableScreenHandler.PAGE_WORK);
		player.sendMessage(Text.translatable("gui.the_dream_table.repairing"), true);
		return true;
	}

	public static boolean takeDown(TableSession session, ServerPlayerEntity player, FarmLoader.Located farm) {
		if (farm.farm().operation() != FarmOperation.READY) {
			player.sendMessage(Text.translatable("gui.the_dream_table.repair_busy"), true);
			return false;
		}
		FarmTemplate template = checked(session, player, farm);
		if (template == null) {
			return false;
		}
		FarmSite site = farm.farm().site();
		BitSet placed = farm.farm().placed();
		// Refused before the record is released: a farm that can't be taken apart must stay listed and protected.
		if (!FarmDismantler.mayTakeDown(farm.world(), player, template, site, farm.farm().swaps(), placed)) {
			player.sendMessage(Text.translatable("gui.the_dream_table.take_down_denied"), true);
			return false;
		}
		// The job and its area claim belong to this table, which ticks and keeps them, not to the table the farm came from.
		FarmAreas areas = FarmAreas.of(farm.world());
		// Snapshot before the record is released: releasing drops its alert state, which a partial Take Down brings back.
		NbtCompound alerts = areas.alerts(farm.farm().instanceId()).map(saved -> saved.write(new NbtCompound())).orElse(null);
		FarmDismantler job = new FarmDismantler(player.getUuid(), template, site, farm.farm().swaps(), placed, farm.farm().paid(),
				session.table().getPos(), new FarmDismantler.Identity(farm.farm().instanceId(), farm.farm().ownerUuid(), farm.farm().customName(),
						farm.farm().grants(), farm.farm().invites(), alerts));
		areas.release(farm.farm().key());
		areas.claim(job.areaKey(), site.footprint(template));
		session.table().startDismantling(job);
		player.sendMessage(Text.translatable("gui.the_dream_table.taking_down"), true);
		return true;
	}

	/**
	 * The design the farm was built with, saved with it, when it is in this table's dimension; otherwise null, after
	 * telling the player. Repairs and take-aparts always use it, so an update that changes the farm doesn't matter.
	 */
	private static FarmTemplate checked(TableSession session, ServerPlayerEntity player, FarmLoader.Located farm) {
		if (session.table().getWorld() != farm.world()) {
			player.sendMessage(Text.translatable("gui.the_dream_table.other_dimension"), true);
			return null;
		}
		FarmTemplate template = farm.farm().layout();
		if (template == null) {
			player.sendMessage(Text.translatable("gui.the_dream_table.farm_design_lost"), true);
		}
		return template;
	}
}
