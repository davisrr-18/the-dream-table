package com.davisr.thedreamtable.platform;

import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/** The loader's "may this player break this block" hook, which claim and protection mods cancel breaks through. */
public interface BlockBreakGuard {
	boolean allowsBreak(ServerWorld world, PlayerEntity player, BlockPos pos, BlockState state);
}
