package com.davisr.thedreamtable.block.entity;

import com.davisr.thedreamtable.farm.BuildPermission;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.inventory.ContainerLock;
import net.minecraft.inventory.Inventory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The containers "Deliver from Nearby" may take from: chests (trapped ones too), barrels and placed shulker boxes within
 * {@link #RADIUS} blocks of the table. Nothing is opened, so the rules stand in for what opening would check: locked
 * containers and loot containers nobody opened yet are skipped, and so is any block the player may not break
 * ({@link BuildPermission#canBreak}, which claim mods answer). Chunks that are not loaded are never read, and the
 * contents of shulker box items are never looked into. Each chest half is its own inventory, so a double chest is
 * never counted twice.
 */
final class NearbyContainers {
	static final int RADIUS = 6;

	private NearbyContainers() {
	}

	/** The usable inventories nearest first, and how many containers were skipped for being locked, unopened loot or protected. */
	record Found(List<Inventory> inventories, int skipped) {
	}

	static Found find(ServerWorld world, ServerPlayerEntity player, BlockPos table) {
		List<BlockEntity> candidates = new ArrayList<>();
		BlockPos.Mutable cursor = new BlockPos.Mutable();
		for (int x = -RADIUS; x <= RADIUS; x++) {
			for (int y = -RADIUS; y <= RADIUS; y++) {
				for (int z = -RADIUS; z <= RADIUS; z++) {
					cursor.set(table, x, y, z);
					if (!withinRange(table, cursor) || !world.isChunkLoaded(cursor)) {
						continue;
					}
					BlockEntity found = world.getBlockEntity(cursor);
					if (found != null && supported(found)) {
						candidates.add(found);
					}
				}
			}
		}
		candidates.sort(Comparator.comparingDouble(candidate -> candidate.getPos().getSquaredDistance(table)));
		List<Inventory> usable = new ArrayList<>();
		int skipped = 0;
		for (BlockEntity candidate : candidates) {
			if (locked(candidate) || unopenedLoot(candidate)
					|| !BuildPermission.canBreak(world, player, candidate.getPos(), candidate.getCachedState())) {
				skipped++;
			} else {
				usable.add((Inventory) candidate);
			}
		}
		return new Found(usable, skipped);
	}

	/** True within a ball of {@link #RADIUS} blocks around the table, measured block centre to block centre. */
	static boolean withinRange(BlockPos table, BlockPos pos) {
		return table.getSquaredDistance(pos) <= (double) RADIUS * RADIUS;
	}

	static boolean supported(BlockEntity entity) {
		return entity instanceof ChestBlockEntity || entity instanceof BarrelBlockEntity || entity instanceof ShulkerBoxBlockEntity;
	}

	/** Any lock counts, even one the player holds the key for: opening is not what this does. */
	static boolean locked(BlockEntity entity) {
		return !entity.createComponentMap().getOrDefault(DataComponentTypes.LOCK, ContainerLock.EMPTY).equals(ContainerLock.EMPTY);
	}

	/** A container with a loot table still holds nothing real until someone opens it. */
	static boolean unopenedLoot(BlockEntity entity) {
		return entity instanceof LootableContainerBlockEntity loot && loot.getLootTable() != null;
	}
}
