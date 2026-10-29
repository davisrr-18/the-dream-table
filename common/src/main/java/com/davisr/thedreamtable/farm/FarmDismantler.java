package com.davisr.thedreamtable.farm;

import com.davisr.thedreamtable.network.TableNoticePayload;
import com.davisr.thedreamtable.TheDreamTableMod;
import com.davisr.thedreamtable.block.entity.MaterialLedger;
import com.davisr.thedreamtable.farm.alert.FarmAlerts;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
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
import org.jetbrains.annotations.Nullable;

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
	/** The table running the job, which holds its area claim and chunk. Differs from the site's table when a farm is taken down at another table. */
	private final BlockPos host;
	/** The design a Take Down runs on, saved with the job because its saved id is registered only while the farm's record exists. */
	private final @Nullable FarmTemplate layout;
	/** What a finished farm's record needs to come back with the same identity when the job leaves blocks standing; null for a discard. */
	private final @Nullable Identity identity;
	/** Steps whose blocks the player may not break, so they stay standing and the farm stays on record. */
	private final BitSet standing = new BitSet();
	private int next;
	private int removed;
	/** Whether the player was already told their inventory was full during this job. Not saved. */
	private boolean toldFull;
	/** The job waited for the player to come back online. Not saved. */
	private boolean playerAway;

	/**
	 * The owner-managed data of a finished farm, kept so its record can be restored if Take Down leaves part of it standing.
	 * {@code alerts} is the saved alert state, which releasing the record would otherwise drop with it.
	 */
	public record Identity(UUID instanceId, UUID owner, @Nullable String customName, Map<UUID, FarmGrant> grants, Map<UUID, FarmInvite> invites,
			@Nullable NbtCompound alerts) {
		public Identity(UUID instanceId, UUID owner, @Nullable String customName, Map<UUID, FarmGrant> grants, Map<UUID, FarmInvite> invites) {
			this(instanceId, owner, customName, grants, invites, null);
		}
	}

	public FarmDismantler(UUID player, String farmId, long signature, FarmSite site, Map<Item, Item> swaps, BitSet placed, BitSet paid, int buildIndex) {
		this(player, farmId, signature, site, swaps, placed, paid, buildIndex, areaSpot(site), site.table(), null, null);
	}

	/** Takes a finished farm down from {@code host}, with the design it was built with. */
	public FarmDismantler(UUID player, FarmTemplate layout, FarmSite site, Map<Item, Item> swaps, BitSet placed, BitSet paid, BlockPos host,
			Identity identity) {
		this(player, layout.id(), layout.signature(), site, swaps, placed, paid, layout.steps().size(), areaSpot(site), host, layout, identity);
	}

	private FarmDismantler(UUID player, String farmId, long signature, FarmSite site, Map<Item, Item> swaps, BitSet placed, BitSet paid, int buildIndex,
			long areaSpot, BlockPos host, @Nullable FarmTemplate layout, @Nullable Identity identity) {
		this.identity = identity;
		this.player = player;
		this.farmId = farmId;
		this.signature = signature;
		this.site = site;
		this.swaps = swaps;
		this.placed = placed;
		this.paid = paid;
		this.areaSpot = areaSpot;
		this.host = host;
		this.layout = layout;
		this.next = buildIndex - 1;
	}

	@Nullable Identity identity() {
		return identity;
	}

	public UUID player() {
		return player;
	}

	public FarmAreas.Key areaKey() {
		return FarmAreas.Key.dismantle(host, player, areaSpot);
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
				TableNoticePayload.problem(online, Text.translatable("gui.the_dream_table.discard_changed"));
			}
			return true;
		}
		if (online == null || !online.isAlive()) {
			playerAway = true;
			return false;
		}
		if (playerAway) {
			playerAway = false;
			TableNoticePayload.info(online, Text.translatable("gui.the_dream_table.dismantle_continues"));
		}
		ChunkKeeper.keepTable(world, host);
		Batch batch = new Batch();
		next = Math.min(next, template.steps().size() - 1);
		try {
			for (int checked = 0; next >= 0 && checked < STEPS_PER_TICK && batch.removed < BLOCKS_PER_TICK; checked++) {
				if (!removeStep(world, online, template, next, batch)) {
					break;
				}
				next--;
			}
		} finally {
			// What was already taken out of the world reaches the player even if a later step fails.
			removed += batch.removed;
			if (batch.hand(world, online) && !toldFull) {
				toldFull = true;
				Handover.tellDropped(online);
			}
		}
		if (next >= 0) {
			return false;
		}
		if (removed > 0) {
			TableNoticePayload.info(online, Text.translatable("gui.the_dream_table.dismantled", removed));
			world.playSound(null, online.getBlockPos(), SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.8F, 0.8F);
		}
		if (!standing.isEmpty()) {
			online.sendMessage(Text.translatable(identity != null ? "gui.the_dream_table.dismantle_kept" : "gui.the_dream_table.dismantle_protected",
					standing.cardinality()), false);
		}
		return true;
	}

	/**
	 * Called once when the job is over, however it ended. A farm that still has blocks the job could not remove, or has
	 * not reached yet, goes back on record with its original identity, name, collaborators and invitations, and only
	 * those steps as placed and paid, so it stays listed and protected instead of being orphaned.
	 */
	public void restoreRecord(ServerWorld world) {
		if (identity == null || layout == null) {
			return;
		}
		BitSet left = (BitSet) standing.clone();
		for (int index = placed.nextSetBit(0); index >= 0 && index <= next; index = placed.nextSetBit(index + 1)) {
			left.set(index);
		}
		if (left.isEmpty()) {
			return;
		}
		BitSet leftPaid = (BitSet) paid.clone();
		leftPaid.and(left);
		FarmAreas areas = FarmAreas.of(world);
		areas.recordFinished(identity.owner(), layout, site, swaps, identity.instanceId(), identity.customName(),
				identity.grants(), identity.invites(), left, leftPaid);
		if (identity.alerts() != null) {
			areas.restoreAlerts(identity.instanceId(), FarmAlerts.read(identity.alerts()));
		}
	}

	/**
	 * Whether the player may break the blocks a Take Down would remove. Parts in unloaded chunks can't be asked and
	 * are left to the job, which tells the player about anything it still finds protected.
	 */
	public static boolean mayTakeDown(ServerWorld world, PlayerEntity player, FarmTemplate template, FarmSite site, Map<Item, Item> swaps, BitSet placed) {
		if (!player.getAbilities().allowModifyWorld) {
			return false;
		}
		for (int index = placed.nextSetBit(0); index >= 0 && index < template.steps().size(); index = placed.nextSetBit(index + 1)) {
			FarmStep step = template.steps().get(index);
			BlockPos pos = site.target(template, step);
			if (step.isEntity() || step.state() == null || !world.isChunkLoaded(pos)) {
				continue;
			}
			BlockState current = world.getBlockState(pos);
			if (BlockMatch.stillFarm(current, expected(step, swaps, site)) && !BuildPermission.canBreak(world, player, pos, current)) {
				return false;
			}
		}
		return true;
	}

	private static BlockState expected(FarmStep step, Map<Item, Item> swaps, FarmSite site) {
		return site.orient(MaterialSwaps.apply(step.state(), swaps));
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
		BlockState current = world.getBlockState(pos);
		if (!BlockMatch.stillFarm(current, expected(step, swaps, site))) {
			return true;
		}
		if (!BuildPermission.canBreak(world, player, pos, current)) {
			standing.set(index);
			return true;
		}
		if (!current.getFluidState().isEmpty()) {
			batch.liquids.add(pos);
		}
		// Contents are copied and the inventory emptied before the block goes, and the batch hands them over even if this step fails,
		// so a retry finds the inventory already empty. The refund and the count are recorded exactly when the block is gone:
		// a step that throws while the block still stands is retried whole, one that throws after removing it is not repeated.
		takeContents(world, pos, batch.contents);
		batch.cleared.add(pos);
		boolean gone = false;
		try {
			world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
			gone = true;
		} finally {
			if (gone || !BlockMatch.stillFarm(world.getBlockState(pos), expected(step, swaps, site))) {
				boolean first = batch.removed == 0;
				batch.removed++;
				// Water, and lava or powder snow placed since they too hand back their empty bucket, refund nothing; see FarmSpawns.recordsAsPaid.
				if (FarmSpawns.refundsOnTakeDown(step.item(), paid.get(index))) {
					batch.refunds.merge(swaps.getOrDefault(step.item(), step.item()), 1, Integer::sum);
				}
				if (first) {
					world.spawnParticles(ParticleTypes.POOF, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4, 0.25, 0.25, 0.25, 0.01);
				}
			}
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
		// Copied in full before anything is emptied, so a failure while reading leaves both sides as they were.
		List<ItemStack> taken = new ArrayList<>();
		for (int slot = 0; slot < inventory.size(); slot++) {
			ItemStack stack = inventory.getStack(slot);
			if (!stack.isEmpty()) {
				taken.add(stack.copy());
			}
		}
		contents.addAll(taken);
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
		nbt.putLong("Host", host.asLong());
		if (layout != null) {
			nbt.put("Layout", TemplateCodec.encode(layout, true));
		}
		nbt.putLongArray("Standing", standing.toLongArray());
		if (identity != null) {
			nbt.putUuid("Instance", identity.instanceId());
			nbt.putUuid("Owner", identity.owner());
			if (identity.customName() != null) {
				nbt.putString("CustomName", identity.customName());
			}
			FarmGrant.writeAll(nbt, "Grants", identity.grants());
			FarmInvite.writeAll(nbt, "Invites", identity.invites());
			if (identity.alerts() != null) {
				nbt.put("Alerts", identity.alerts().copy());
			}
		}
	}

	/** A saved job, or null when it is unreadable; a job that can't be read doesn't take the table's other data with it, and its table keeps the saved data unchanged. */
	public static @Nullable FarmDismantler read(NbtCompound nbt) {
		try {
			return readJob(nbt);
		} catch (RuntimeException corrupt) {
			TheDreamTableMod.LOGGER.error("Unreadable Take Down job; its saved data is kept unchanged", corrupt);
			return null;
		}
	}

	/** The area claim a saved job holds, read without its layout, so the claim of a job that can't be read is still freed. Null when even that is unreadable. */
	public static FarmAreas.@Nullable Key claimOf(NbtCompound nbt) {
		try {
			if (!nbt.containsUuid("Player")) {
				return null;
			}
			long areaSpot = nbt.contains("AreaSpot", NbtElement.LONG_TYPE) ? nbt.getLong("AreaSpot") : 0L;
			BlockPos host = nbt.contains("Host", NbtElement.LONG_TYPE) ? BlockPos.fromLong(nbt.getLong("Host")) : FarmSite.read(nbt.getCompound("Site")).table();
			return FarmAreas.Key.dismantle(host, nbt.getUuid("Player"), areaSpot);
		} catch (RuntimeException unreadable) {
			return null;
		}
	}

	private static @Nullable FarmDismantler readJob(NbtCompound nbt) {
		if (!nbt.containsUuid("Player")) {
			return null;
		}
		long areaSpot = nbt.contains("AreaSpot", NbtElement.LONG_TYPE) ? nbt.getLong("AreaSpot") : 0L;
		String farmId = nbt.getString("Farm");
		FarmSite site = FarmSite.read(nbt.getCompound("Site"));
		FarmTemplate layout = null;
		if (nbt.contains("Layout", NbtElement.COMPOUND_TYPE)) {
			layout = TemplateCodec.decode(farmId, nbt.getCompound("Layout"));
			FarmTemplates.keepSaved(layout);
		}
		BlockPos host = nbt.contains("Host", NbtElement.LONG_TYPE) ? BlockPos.fromLong(nbt.getLong("Host")) : site.table();
		Identity identity = null;
		if (nbt.containsUuid("Instance") && nbt.containsUuid("Owner")) {
			String name = nbt.contains("CustomName", NbtElement.STRING_TYPE) ? nbt.getString("CustomName") : null;
			identity = new Identity(nbt.getUuid("Instance"), nbt.getUuid("Owner"), name,
					FarmGrant.readAll(nbt, "Grants"), FarmInvite.readAll(nbt, "Invites"),
					nbt.contains("Alerts", NbtElement.COMPOUND_TYPE) ? nbt.getCompound("Alerts") : null);
		}
		FarmDismantler job = new FarmDismantler(nbt.getUuid("Player"), farmId, nbt.getLong("FarmSignature"), site, MaterialLedger.readSwaps(nbt),
				BitSet.valueOf(nbt.getLongArray("PlacedSteps")), BitSet.valueOf(nbt.getLongArray("PaidSteps")), nbt.getInt("Next") + 1, areaSpot, host, layout,
				identity);
		job.removed = nbt.getInt("Removed");
		job.standing.or(BitSet.valueOf(nbt.getLongArray("Standing")));
		return job;
	}
}
