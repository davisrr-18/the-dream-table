package com.davisr.thedreamtable.forge;

import com.davisr.thedreamtable.platform.BlockBreakGuard;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.Result;
import net.minecraftforge.event.level.BlockEvent;

/**
 * Asks Forge's block break event, which claim mods deny (by {@code Result.DENY} or a listener that returns true; a listener
 * that only calls {@code setCanceled} on the old API is not seen in EventBus 7). Forge 61 itself stops a break only when the event's result is
 * DENY (a listener that returns true only ends the propagation), so both are read.
 */
final class ForgeBlockBreakGuard implements BlockBreakGuard {
	@Override
	public boolean allowsBreak(ServerWorld world, PlayerEntity player, BlockPos pos, BlockState state) {
		BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(world, pos, state, player, Result.DEFAULT);
		// post returns true when a listener canceled the event.
		boolean canceled = BlockEvent.BreakEvent.BUS.post(event);
		return !canceled && !event.getResult().isDenied();
	}
}
