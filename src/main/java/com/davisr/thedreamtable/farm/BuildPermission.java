package com.davisr.thedreamtable.farm;

import com.davisr.thedreamtable.platform.Platform;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Asks the world whether a player may build at a spot, the way their own hands would be asked:
 * adventure mode, spawn protection, the world border, and any claim mod that hooks those checks.
 * Breaking also asks the loader's block break hook, which claim mods use to cancel breaks in protected land.
 */
public final class BuildPermission {
	private BuildPermission() {
	}

	public static boolean canPlace(ServerWorld world, PlayerEntity player, BlockPos pos) {
		return player.getAbilities().allowModifyWorld && world.canPlayerModifyAt(player, pos);
	}

	public static boolean canBreak(ServerWorld world, PlayerEntity player, BlockPos pos, BlockState state) {
		return canPlace(world, player, pos)
				&& Platform.blockBreaks().allowsBreak(world, player, pos, state);
	}

	/** True when the player may place at every spot the farm uses. Checked once before a new build starts. */
	public static boolean canBuildAll(ServerWorld world, ServerPlayerEntity player, FarmSite site, FarmTemplate template) {
		for (FarmStep step : template.steps()) {
			if (!canPlace(world, player, site.target(template, step))) {
				return false;
			}
		}
		return true;
	}
}
