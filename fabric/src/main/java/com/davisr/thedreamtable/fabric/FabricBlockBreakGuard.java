package com.davisr.thedreamtable.fabric;

import com.davisr.thedreamtable.platform.BlockBreakGuard;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/** Asks Fabric API's block break event, which claim mods cancel. */
final class FabricBlockBreakGuard implements BlockBreakGuard {
	@Override
	public boolean allowsBreak(ServerWorld world, PlayerEntity player, BlockPos pos, BlockState state) {
		return PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(world, player, pos, state, world.getBlockEntity(pos));
	}
}
