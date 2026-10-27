package com.davisr.thedreamtable.farm;

import com.davisr.thedreamtable.block.entity.TableSession;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

/** Mobs and minecarts the builder places, and the empty bucket it hands back after pouring water. */
final class FarmSpawns {
	private FarmSpawns() {
	}

	/** Returns false when nothing was spawned, so the build keeps the item and tries again. */
	static boolean spawn(ServerWorld world, BlockPos pos, FarmStep step) {
		Entity entity = step.entity().create(world);
		if (entity == null) {
			return false;
		}
		double y = pos.getY() + (step.entity() == EntityType.HOPPER_MINECART || step.minecart() ? 0.0625 : 0.0);
		entity.setPosition(pos.getX() + 0.5, y, pos.getZ() + 0.5);
		if (entity instanceof MobEntity mob) {
			mob.setPersistent();
			mob.setAiDisabled(step.noAi());
		}
		Entity cart = step.minecart() ? EntityType.MINECART.create(world) : null;
		if (cart == null) {
			return world.spawnEntity(entity);
		}
		cart.setPosition(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
		if (!world.spawnEntity(cart)) {
			return false;
		}
		if (!world.spawnEntity(entity)) {
			cart.discard();
			return false;
		}
		entity.startRiding(cart, true);
		return true;
	}

	/**
	 * True when a living entity of the step's kind already stands in the step's block, like the villagers and zombie of
	 * a repaired iron farm, so it isn't spawned twice. Only the entity's own position counts, not its width, so one in
	 * the next cell doesn't.
	 */
	static boolean present(ServerWorld world, BlockPos pos, FarmStep step) {
		Box cell = new Box(pos.getX() - 0.2, pos.getY() - 0.5, pos.getZ() - 0.2, pos.getX() + 1.2, pos.getY() + 1.5, pos.getZ() + 1.2);
		return !world.getEntitiesByType(step.entity(), cell, entity -> entity.isAlive() && cell.contains(entity.getPos())).isEmpty();
	}

	static void returnBucket(ServerWorld world, TableSession table) {
		ItemStack bucket = new ItemStack(Items.BUCKET);
		ServerPlayerEntity player = table.getOwner() == null ? null : world.getServer().getPlayerManager().getPlayer(table.getOwner());
		if (player != null) {
			if (table.build().shouldNotifyDropped(Handover.give(player, bucket))) {
				Handover.tellDropped(player);
			}
			return;
		}
		BlockPos pos = table.getPos();
		ItemScatterer.spawn(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, bucket);
	}
}
