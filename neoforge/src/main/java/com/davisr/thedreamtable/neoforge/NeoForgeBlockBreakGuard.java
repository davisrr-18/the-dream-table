package com.davisr.thedreamtable.neoforge;

import com.davisr.thedreamtable.platform.BlockBreakGuard;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;

/** Asks NeoForge's block break event, which claim mods cancel. */
final class NeoForgeBlockBreakGuard implements BlockBreakGuard {
	@Override
	public boolean allowsBreak(ServerWorld world, PlayerEntity player, BlockPos pos, BlockState state) {
		return !NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(world, pos, state, player)).isCanceled();
	}
}
