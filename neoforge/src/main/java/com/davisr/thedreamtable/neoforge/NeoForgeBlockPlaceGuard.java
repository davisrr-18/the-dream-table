package com.davisr.thedreamtable.neoforge;

import com.davisr.thedreamtable.platform.BlockPlaceGuard;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Posts NeoForge's native place event, which claim mods cancel, and reads whether it was canceled. Nothing is placed.
 * The game itself posts this event with the block already set and rolls the snapshot back on a cancel; here it is
 * posted before the real placement, so the snapshot and the event's placed block are the block standing there now.
 * Accepted risk: another listener that reacts to the event as a completed placement (logging, statistics) sees one
 * that has not happened yet. Claim mods decide by player and position, which are exact.
 */
final class NeoForgeBlockPlaceGuard implements BlockPlaceGuard {
	@Override
	public boolean allowsPlace(ServerWorld world, PlayerEntity player, BlockPos pos, BlockState placed) {
		BlockSnapshot snapshot = BlockSnapshot.create(world.getRegistryKey(), world, pos);
		BlockEvent.EntityPlaceEvent event = new BlockEvent.EntityPlaceEvent(snapshot, world.getBlockState(pos.down()), player);
		return !NeoForge.EVENT_BUS.post(event).isCanceled();
	}
}
