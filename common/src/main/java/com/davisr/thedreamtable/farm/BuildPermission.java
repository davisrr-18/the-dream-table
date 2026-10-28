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
 * Breaking also asks the loader's block break hook, and placing a block asks the loader's place probe, which claim
 * mods use to deny builds in protected land (a replaceable spot such as air breaks nothing, so only the probe reaches them).
 */
public final class BuildPermission {
	private BuildPermission() {
	}

	public static boolean canPlace(ServerWorld world, PlayerEntity player, BlockPos pos) {
		return player.getAbilities().allowModifyWorld && world.canPlayerModifyAt(player, pos);
	}

	/** The vanilla checks plus the loader's place probe for the block about to go there. Asked per step, never for a whole farm. */
	public static boolean canPlace(ServerWorld world, PlayerEntity player, BlockPos pos, BlockState placed) {
		return canPlace(world, player, pos) && Platform.blockPlaces().allowsPlace(world, player, pos, placed);
	}

	public static boolean canBreak(ServerWorld world, PlayerEntity player, BlockPos pos, BlockState state) {
		return canPlace(world, player, pos)
				&& Platform.blockBreaks().allowsBreak(world, player, pos, state);
	}

	/**
	 * True when the player may place at every spot the farm uses, by the cheap vanilla checks only. Checked once before a new
	 * build starts, and for the hologram, over up to 16384 spots; asking the loader's place probe (an event with a snapshot
	 * for NeoForge) for each would be heavy and would show other mods placements that never happen. A spot a claim mod denies
	 * stops the build at that step with the PROTECTED issue instead, where {@link #canPlace(ServerWorld, PlayerEntity, BlockPos, BlockState)} asks.
	 */
	public static boolean canBuildAll(ServerWorld world, ServerPlayerEntity player, FarmSite site, FarmTemplate template) {
		for (FarmStep step : template.steps()) {
			if (!canPlace(world, player, site.target(template, step))) {
				return false;
			}
		}
		return true;
	}
}
