package com.davisr.thedreamtable.farm;

import com.davisr.thedreamtable.block.entity.DreamTableBlockEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public final class FarmBuilder {
	private FarmBuilder() {
	}

	public static boolean placeNext(DreamTableBlockEntity table, ServerWorld world) {
		FarmTemplate template = FarmTemplates.get(table.getSelectedFarm());
		if (template == null) {
			table.finish();
			return false;
		}
		if (table.getBuildIndex() >= template.steps().size()) {
			table.finish();
			return false;
		}

		FarmStep step = template.steps().get(table.getBuildIndex());
		Direction tableFacing = table.getCachedState().get(Properties.HORIZONTAL_FACING);
		Direction facing = table.hasAnchor() ? table.getFarmFacing() : tableFacing;
		BlockPos target = table.hasAnchor()
				? FarmGeometry.toAimed(table.getFarmAnchor(), facing, template, step)
				: FarmGeometry.toWorld(table.getPos(), facing, step);
		if (!step.isEntity()) {
			BlockState existing = world.getBlockState(target);
			BlockState placed = step.state().rotate(FarmGeometry.rotation(facing));
			if (alreadyThere(existing, placed)) {
				table.advance();
				if (table.getBuildIndex() >= template.steps().size()) {
					table.finish();
				}
				return true;
			}
			if (!existing.isReplaceable()) {
				table.obstruct(target, existing);
				return false;
			}
		}
		if (step.item() != null && !table.consumeMaterial(step.item())) {
			table.missingMaterial();
			return false;
		}

		if (step.isEntity()) {
			spawn(world, target, step);
			world.playSound(null, target, SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.BLOCKS, 0.6F, 1.1F);
		} else {
			BlockState placed = step.state().rotate(FarmGeometry.rotation(facing));
			world.setBlockState(target, placed, Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
			if (step.blockNbt() != null && world.getBlockEntity(target) != null) {
				net.minecraft.nbt.NbtCompound data = step.blockNbt().copy();
				data.putInt("x", target.getX());
				data.putInt("y", target.getY());
				data.putInt("z", target.getZ());
				world.getBlockEntity(target).read(data, world.getRegistryManager());
			}
			world.playSound(null, target, placed.getSoundGroup().getPlaceSound(), SoundCategory.BLOCKS, 0.85F, 0.9F + world.random.nextFloat() * 0.2F);
			if (step.item() == Items.WATER_BUCKET) {
				returnBucket(world, table);
			}
		}

		table.advance();
		if (table.getBuildIndex() >= template.steps().size()) {
			settle(world, table, template, facing);
			table.finish();
		}
		return true;
	}

	private static boolean alreadyThere(BlockState existing, BlockState placed) {
		if (!existing.isOf(placed.getBlock())) {
			return false;
		}
		if (!placed.getFluidState().isEmpty()) {
			return existing.getFluidState().getLevel() == placed.getFluidState().getLevel();
		}
		return true;
	}

	private static void settle(ServerWorld world, DreamTableBlockEntity table, FarmTemplate template, Direction facing) {
		for (FarmStep step : template.steps()) {
			if (step.isEntity() || step.state() == null) {
				continue;
			}
			BlockPos pos = table.hasAnchor()
					? FarmGeometry.toAimed(table.getFarmAnchor(), facing, template, step)
					: FarmGeometry.toWorld(table.getPos(), facing, step);
			BlockState state = world.getBlockState(pos);
			if (!state.isAir()) {
				world.updateNeighbors(pos, state.getBlock());
			}
		}
	}

	private static void spawn(ServerWorld world, BlockPos pos, FarmStep step) {
		Entity entity = step.entity().create(world);
		if (entity == null) {
			return;
		}
		double y = pos.getY();
		if (step.entity() == EntityType.HOPPER_MINECART || step.minecart()) {
			y += 0.0625;
		}
		entity.setPosition(pos.getX() + 0.5, y, pos.getZ() + 0.5);
		if (entity instanceof MobEntity mob) {
			mob.setPersistent();
			mob.setAiDisabled(step.noAi());
		}
		if (!step.minecart()) {
			world.spawnEntity(entity);
			return;
		}
		Entity cart = EntityType.MINECART.create(world);
		if (cart == null) {
			world.spawnEntity(entity);
			return;
		}
		cart.setPosition(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
		world.spawnEntity(cart);
		world.spawnEntity(entity);
		entity.startRiding(cart, true);
	}

	private static void returnBucket(ServerWorld world, DreamTableBlockEntity table) {
		ItemStack bucket = new ItemStack(Items.BUCKET);
		ServerPlayerEntity player = table.getOwner() == null ? null : world.getServer().getPlayerManager().getPlayer(table.getOwner());
		if (player != null && player.getInventory().insertStack(bucket) && bucket.isEmpty()) {
			return;
		}
		if (player != null && !bucket.isEmpty()) {
			player.dropItem(bucket, false);
			return;
		}
		ItemScatterer.spawn(world, table.getPos().getX() + 0.5, table.getPos().getY() + 0.5, table.getPos().getZ() + 0.5, bucket);
	}
}
