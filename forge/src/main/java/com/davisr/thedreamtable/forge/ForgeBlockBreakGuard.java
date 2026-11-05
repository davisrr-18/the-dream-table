package com.davisr.thedreamtable.forge;

import com.davisr.thedreamtable.platform.BlockBreakGuard;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;

/** Asks Forge's block break event, which claim mods cancel. */
final class ForgeBlockBreakGuard implements BlockBreakGuard {
	@Override
	public boolean allowsBreak(ServerWorld world, PlayerEntity player, BlockPos pos, BlockState state) {
		// post returns true when a listener canceled the event.
		return !MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(world, pos, state, player));
	}
}
