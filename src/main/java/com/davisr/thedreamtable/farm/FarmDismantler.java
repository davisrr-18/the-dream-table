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
import net.minecraft.nbt.NbtElement;
import net.minecraft.particle.ParticleTypes;
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
	/** Unique reservation for new jobs; zero keeps the reservation used by saves from before per-job keys. */
	private final long areaSpot;
	private int next;
	private int removed;
	/** Whether the player was already told their inventory was full during this job. Not saved. */
	private boolean toldFull;
	/** The job waited for the player to come back online. Not saved. */
	private boolean playerAway;

	public FarmDismantler(UUID player, String farmId, long signature, FarmSite site, Map<Item, Item> swaps, BitSet placed, BitSet paid, int buildIndex) {
		this(player, farmId, signature, site, swaps, placed, paid, buildIndex, areaSpot(site));
	}

	private FarmDismantler(UUID player, String farmId, long signature, FarmSite site, Map<Item, Item> swaps, BitSet placed, BitSet paid, int buildIndex,
			long areaSpot) {
		this.player = player;
		this.farmId = farmId;
		this.signature = signature;
		this.site = site;
		this.swaps = swaps;
		this.placed = placed;
		this.paid = paid;
		this.areaSpot = areaSpot;
		this.next = buildIndex - 1;
	}

	public UUID player() {
		return player;
	}

	public FarmAreas.Key areaKey() {
		return FarmAreas.Key.dismantle(site.table(), player, areaSpot);
	}

	private static long areaSpot(FarmSite site) {
		return (site.anchor() != null ? site.anchor() : site.table()).asLong();
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
			playerAway = true;
			return false;
		}
		if (playerAway) {
			playerAway = false;
			online.sendMessage(Text.translatable("gui.the_dream_table.dismantle_continues"), true);
		}
		ChunkKeeper.keepTable(world, site.table());
		Batch batch = new Batch();
		next = Math.min(next, template.steps().size() - 1);
		for (int checked = 0; next >= 0 && checked < STEPS_PER_TICK && batch.removed < BLOCKS_PER_TICK; checked++) {
			if (!removeStep(world, online, template, next, batch)) {
				break;
			}
			next--;
		}
		removed += batch.removed;
		if (batch.hand(world, online) && !toldFull) {
			toldFull = true;
			Handover.tellDropped(online);
		}
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
		ChunkKeeper.keepSpot(world, pos);
		if (!world.isChunkLoaded(pos)) {
			return false;
		}
		BlockState expected = MaterialSwaps.apply(step.state(), swaps).rotate(FarmGeometry.rotation(site.facing()));
		BlockState current = world.getBlockState(pos);
		if (!BlockMatch.stillFarm(current, expected) || !BuildPermission.canBreak(world, player, pos, current)) {
			return true;
		}
		if (!current.getFluidState().isEmpty()) {
			batch.liquids.add(pos);
		}
		takeContents(world, pos, batch.contents);
		batch.cleared.add(pos);
		world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
		if (batch.removed == 0) {
			world.spawnParticles(ParticleTypes.POOF, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4, 0.25, 0.25, 0.25, 0.01);
		}
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
		private final List<BlockPos> cleared = new ArrayList<>();
		private int removed;

		/** Returns true when the player's inventory was full and something was dropped at their feet. */
		private boolean hand(ServerWorld world, PlayerEntity player) {
			drain(world, liquids);
			settle(world, cleared);
			boolean dropped = false;
			for (Map.Entry<Item, Integer> refund : refunds.entrySet()) {
				dropped |= Handover.giveAll(player, refund.getKey(), refund.getValue());
			}
			for (ItemStack stack : contents) {
				dropped |= Handover.give(player, stack);
			}
			return dropped;
		}
	}

	/**
	 * Removal itself skips neighbor updates, so whatever leaned on a removed block, like cane or bamboo that grew,
	 * crops planted on farmland, torches, redstone or an extended piston's head, would float. Once a batch is out,
	 * its neighbors are told, and what lost its support drops as items, as it would when mined by hand.
	 */
	private static void settle(ServerWorld world, List<BlockPos> cleared) {
		BlockState air = Blocks.AIR.getDefaultState();
		for (BlockPos pos : cleared) {
			air.updateNeighbors(world, pos, Block.NOTIFY_ALL);
			world.updateNeighbors(pos, Blocks.AIR);
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
		nbt.putLong("AreaSpot", areaSpot);
	}

	public static FarmDismantler read(NbtCompound nbt) {
		if (!nbt.containsUuid("Player")) {
			return null;
		}
		long areaSpot = nbt.contains("AreaSpot", NbtElement.LONG_TYPE) ? nbt.getLong("AreaSpot") : 0L;
		FarmDismantler job = new FarmDismantler(nbt.getUuid("Player"), nbt.getString("Farm"), nbt.getLong("FarmSignature"),
				FarmSite.read(nbt.getCompound("Site")), MaterialLedger.readSwaps(nbt),
				BitSet.valueOf(nbt.getLongArray("PlacedSteps")), BitSet.valueOf(nbt.getLongArray("PaidSteps")), nbt.getInt("Next") + 1, areaSpot);
		job.removed = nbt.getInt("Removed");
		return job;
	}
}
