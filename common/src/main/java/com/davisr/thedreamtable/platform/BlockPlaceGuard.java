package com.davisr.thedreamtable.platform;

import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * The loader's "may this player place this block here" probe, which claim and protection mods answer through
 * place or break events. It only asks; it never places anything. Placing into a replaceable block (air, water, grass)
 * breaks nothing, so {@link BlockBreakGuard} alone never reaches a claim mod that guards that spot.
 */
public interface BlockPlaceGuard {
	/** False when a protection mod denies {@code player} placing {@code placed} at {@code pos}. */
	boolean allowsPlace(ServerWorld world, PlayerEntity player, BlockPos pos, BlockState placed);
}
