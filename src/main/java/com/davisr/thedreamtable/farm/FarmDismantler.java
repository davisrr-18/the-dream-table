package com.davisr.thedreamtable.farm;

import com.davisr.thedreamtable.block.entity.MaterialLedger;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Takes apart a discarded, unfinished build a few blocks per tick, last step first, and hands the player back what it used.
 * Only steps the table placed itself are removed, and only while the same block is still there with the
 * direction, half and axis it was placed with ({@link BlockMatch#sameShape}). A block the player turned, replaced,
 * or built on, and everything that was already in place before the build, stays untouched.
 * Opening a door or powering a part doesn't count as a change, so those are still taken apart.
 * It works only while the player is online, since refunds go to them and their permission to break is checked.
 */
public final class FarmDismantler {
	private static final int BLOCKS_PER_TICK = 32;
	private static final int STEPS_PER_TICK = 512;

	private final UUID player;
	private final String farmId;
	private final long signature;
	private final FarmSite site;
	private final Map<Item, Item> swaps;
	private final BitSet placed;
	private final BitSet paid;
	private int next;
	private int removed;

	public FarmDismantler(UUID player, String farmId, long signature, FarmSite site, Map<Item, Item> swaps, BitSet placed, BitSet paid, int buildIndex) {
		this.player = player;
		this.farmId = farmId;
		this.signature = signature;
		this.site = site;
		this.swaps = swaps;
		this.placed = placed;
		this.paid = paid;
		this.next = buildIndex - 1;
	}

	public UUID player() {
		return player;
	}

	public FarmAreas.Key areaKey() {
		return FarmAreas.Key.dismantle(site.table(), player);
	}

	/** Removes the next batch. Returns true once the job is over and can be forgotten. */
	public boolean tick(ServerWorld world) {
		FarmTemplate template = FarmTemplates.get(farmId);
		ServerPlayerEntity online = world.getServer().getPlayerManager().getPlayer(player);
		if (template == null || template.signature() != signature) {
			if (online != null) {
				online.sendMessage(Text.translatable("gui.the_dream_table.discard_changed"), true);
			}
			return true;
		}
		if (online == null) {
			return false;
		}
		Batch batch = new Batch();
		next = Math.min(next, template.steps().size() - 1);
		for (int checked = 0; next >= 0 && checked < STEPS_PER_TICK && batch.removed < BLOCKS_PER_TICK; checked++) {
			if (!removeStep(world, online, template, next, batch)) {
				break;
			}
			next--;
		}
		removed += batch.removed;
		batch.hand(world, online);
		if (next >= 0) {
			return false;
		}
		if (removed > 0) {
			online.sendMessage(Text.translatable("gui.the_dream_table.dismantled", removed), true);
			world.playSound(null, online.getBlockPos(), SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.8F, 0.8F);
		}
		return true;
	}

	/** Returns false when the step's chunk isn't loaded, so the job waits there instead of skipping it. */
	private boolean removeStep(ServerWorld world, PlayerEntity player, FarmTemplate template, int index, Batch batch) {
		FarmStep step = template.steps().get(index);
		if (step.isEntity() || step.state() == null || !placed.get(index)) {
			return true;
		}
		BlockPos pos = site.target(template, step);
		if (!world.isChunkLoaded(pos)) {
			return false;
		}
		BlockState expected = MaterialSwaps.apply(step.state(), swaps).rotate(FarmGeometry.rotation(site.facing()));
		BlockState current = world.getBlockState(pos);
		if (!BlockMatch.sameShape(current, expected) || !BuildPermission.canBreak(world, player, pos, current)) {
			return true;
		}
		if (!current.getFluidState().isEmpty()) {
			batch.liquids.add(pos);
		}
		takeContents(world, pos, batch.contents);
		world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
		batch.removed++;
		// Water already handed its empty bucket back when it was poured, so it refunds nothing.
		if (paid.get(index) && step.item() != null && step.item() != Items.WATER_BUCKET) {
			batch.refunds.merge(swaps.getOrDefault(step.item(), step.item()), 1, Integer::sum);
		}
		return true;
	}

	/** What one tick removed, handed to the player together at the end of the tick. */
	private static final class Batch {
		private final Map<Item, Integer> refunds = new LinkedHashMap<>();
		private final List<ItemStack> contents = new ArrayList<>();
		private final List<BlockPos> liquids = new ArrayList<>();
		private int removed;

		private void hand(ServerWorld world, PlayerEntity player) {
			drain(world, liquids);
			for (Map.Entry<Item, Integer> refund : refunds.entrySet()) {
				giveAll(player, refund.getKey(), refund.getValue());
			}
			for (ItemStack stack : contents) {
				give(player, stack);
			}
		}
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

	public void write(NbtCompound nbt) {
		nbt.putUuid("Player", player);
		nbt.putString("Farm", farmId);
		nbt.putLong("FarmSignature", signature);
		NbtCompound where = new NbtCompound();
		site.write(where);
		nbt.put("Site", where);
		MaterialLedger.writeSwaps(nbt, swaps);
		nbt.putLongArray("PlacedSteps", placed.toLongArray());
		nbt.putLongArray("PaidSteps", paid.toLongArray());
		nbt.putInt("Next", next);
		nbt.putInt("Removed", removed);
	}

	public static FarmDismantler read(NbtCompound nbt) {
		if (!nbt.containsUuid("Player")) {
			return null;
		}
		FarmDismantler job = new FarmDismantler(nbt.getUuid("Player"), nbt.getString("Farm"), nbt.getLong("FarmSignature"),
				FarmSite.read(nbt.getCompound("Site")), MaterialLedger.readSwaps(nbt),
				BitSet.valueOf(nbt.getLongArray("PlacedSteps")), BitSet.valueOf(nbt.getLongArray("PaidSteps")), nbt.getInt("Next") + 1);
		job.removed = nbt.getInt("Removed");
		return job;
	}
}
