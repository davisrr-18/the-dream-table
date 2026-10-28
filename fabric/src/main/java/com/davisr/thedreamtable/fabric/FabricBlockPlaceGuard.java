package com.davisr.thedreamtable.fabric;

import com.davisr.thedreamtable.platform.BlockPlaceGuard;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Fabric API has no event for placing a block, so this asks the block break event for the target position, with the
 * block that stands there now. Claim and protection mods on Fabric decide by player and position, not by block, so a
 * denied break at a spot means a denied build there. Nothing is broken; the event is only asked. A mod that cancels
 * the break of particular blocks can still deny a spot holding one of them, which only makes the check stricter.
 */
final class FabricBlockPlaceGuard implements BlockPlaceGuard {
	@Override
	public boolean allowsPlace(ServerWorld world, PlayerEntity player, BlockPos pos, BlockState placed) {
		return PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(world, player, pos, world.getBlockState(pos), world.getBlockEntity(pos));
	}
}
