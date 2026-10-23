package com.davisr.thedreamtable.farm;

import com.davisr.thedreamtable.block.entity.TableSession;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Takes apart an unfinished build when it is discarded and hands the player back what it used.
 * Only steps the table placed itself are removed, and only while the same block is still there with the
 * direction, half and axis it was placed with ({@link BlockMatch#sameShape}). A block the player turned, replaced,
 * or built on, and everything that was already in place before the build, stays untouched.
 * Opening a door or powering a part doesn't count as a change, so those are still taken apart.
 */
public final class FarmDismantler {
	private FarmDismantler() {
	}

	/** Returns how many blocks were removed. */
	public static int dismantle(ServerWorld world, TableSession table, PlayerEntity player) {
		FarmTemplate template = table.template();
		if (template == null) {
			return 0;
		}
		Direction facing = FarmBuilder.facing(table);
		Map<Item, Integer> refunds = new LinkedHashMap<>();
		List<ItemStack> contents = new ArrayList<>();
		List<BlockPos> liquids = new ArrayList<>();
		int removed = 0;
		int last = Math.min(table.getBuildIndex(), template.steps().size()) - 1;
		for (int index = last; index >= 0; index--) {
			FarmStep step = template.steps().get(index);
			if (step.isEntity() || step.state() == null || !table.wasPlaced(index)) {
				continue;
			}
			BlockPos pos = FarmBuilder.target(table, template, step, facing);
			BlockState expected = table.swapState(step.state()).rotate(FarmGeometry.rotation(facing));
			BlockState current = world.getBlockState(pos);
			if (!BlockMatch.sameShape(current, expected) || !BuildPermission.canBreak(world, player, pos, current)) {
				continue;
			}
			if (!current.getFluidState().isEmpty()) {
				liquids.add(pos);
			}
			takeContents(world, pos, contents);
			world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
			removed++;
			if (refundable(table, index, step)) {
				refunds.merge(table.resolve(step.item()), 1, Integer::sum);
			}
		}
		drain(world, liquids);
		for (Map.Entry<Item, Integer> refund : refunds.entrySet()) {
			giveAll(player, refund.getKey(), refund.getValue());
		}
		for (ItemStack stack : contents) {
			give(player, stack);
		}
		if (removed > 0) {
			world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.8F, 0.8F);
		}
		return removed;
	}

	/**
	 * Removal skips neighbor updates, so liquid that already flowed out of a removed source would stay frozen.
	 * Only the liquid touching those spots gets a tick; it then drains on its own, like picking up a source with a bucket.
	 */
	private static void drain(ServerWorld world, List<BlockPos> liquids) {
		for (BlockPos pos : liquids) {
			for (Direction side : Direction.values()) {
				BlockPos next = pos.offset(side);
				FluidState fluid = world.getFluidState(next);
				if (!fluid.isEmpty()) {
					world.scheduleFluidTick(next, fluid.getFluid(), fluid.getFluid().getTickRate(world));
				}
			}
		}
	}

	/** Water already handed its empty bucket back when it was poured, so it refunds nothing. */
	private static boolean refundable(TableSession table, int index, FarmStep step) {
		return table.wasPaid(index) && step.item() != null && step.item() != Items.WATER_BUCKET;
	}

	/** Empties a chest, hopper or similar first, so its items go to the player instead of spilling. */
	private static void takeContents(ServerWorld world, BlockPos pos, List<ItemStack> contents) {
		if (!(world.getBlockEntity(pos) instanceof Inventory inventory)) {
			return;
		}
		for (int slot = 0; slot < inventory.size(); slot++) {
			ItemStack stack = inventory.getStack(slot);
			if (!stack.isEmpty()) {
				contents.add(stack.copy());
			}
		}
		inventory.clear();
	}

	private static void giveAll(PlayerEntity player, Item item, int count) {
		int max = Math.max(1, item.getMaxCount());
		while (count > 0) {
			int amount = Math.min(count, max);
			give(player, new ItemStack(item, amount));
			count -= amount;
		}
	}

	private static void give(PlayerEntity player, ItemStack stack) {
		if (!player.getInventory().insertStack(stack) || !stack.isEmpty()) {
			player.dropItem(stack, false);
		}
	}
}
