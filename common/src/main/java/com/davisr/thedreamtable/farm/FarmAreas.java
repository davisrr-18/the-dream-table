package com.davisr.thedreamtable.farm;

import com.davisr.thedreamtable.TheDreamTableMod;
import com.davisr.thedreamtable.block.entity.DreamTableBlockEntity;
import com.davisr.thedreamtable.block.entity.MaterialLedger;
import com.davisr.thedreamtable.farm.alert.FarmAlerts;
import com.davisr.thedreamtable.farm.alert.FarmAlertType;
import net.minecraft.block.BlockState;
import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.PersistentState;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * The areas of every farm in a world, across all tables: farms being built, farms being taken apart, and finished farms.
 * A new farm can't overlap any of them, and a build never clears blocks inside another one.
 * Build and take-apart areas go away with their table. A finished farm stands on its own, so its area stays even
 * without the table, until less than half of its blocks are left, which is checked when a new farm asks to overlap it.
 */
public final class FarmAreas extends PersistentState {
	static final String STATE_KEY = "the_dream_table_farm_areas";
	private final Map<Key, Area> areas = new LinkedHashMap<>();
	private final Map<UUID, RepairLease> repairLeases = new LinkedHashMap<>();
	private final Map<UUID, FarmAlerts> alerts = new LinkedHashMap<>();
	/** Where each finished farm's record is, found without scanning the areas. Rebuilt on demand after the set of records changes. */
	private @Nullable Map<UUID, Key> byInstance;
	/** The saved form of each design, since a finished farm's design never changes and encoding it is the costly part of a save. */
	private static final IdentityCache<FarmTemplate, NbtCompound> ENCODED_LAYOUTS = new IdentityCache<>();
	/**
	 * Records that could not be read, kept as they were saved and written back unchanged, so a farm is not
	 * lost for good to a bug or a missing mod: a later version that can read them gets them back.
	 */
	private final List<NbtCompound> unreadable = new ArrayList<>();

	public enum Kind {
		BUILD, DISMANTLE, FINISHED
	}

	/** What a new farm would run into. */
	public enum Conflict {
		NONE, ACTIVE, FINISHED
	}

	/** One player's farm at one table. Finished farms are also told apart by where they stand, since one table can leave many. */
	public record Key(long table, UUID player, Kind kind, long spot) {
		public static Key build(BlockPos table, UUID player) {
			return new Key(table.asLong(), player, Kind.BUILD, 0L);
		}

		public static Key dismantle(BlockPos table, UUID player, long spot) {
			return new Key(table.asLong(), player, Kind.DISMANTLE, spot);
		}

		static Key finished(UUID player, FarmSite site) {
			BlockPos origin = site.anchor() != null ? site.anchor() : site.table();
			return new Key(site.table().asLong(), player, Kind.FINISHED, origin.asLong() * 4 + site.facing().getHorizontal());
		}
	}

	/**
	 * A finished farm remembers what it built: the farm, the exact design ({@code layout}, kept under a saved id so it
	 * outlives updates that change or remove the farm), the spot, swaps, and when it was finished. The layout is null
	 * only for farms finished by an older version whose design has changed since. {@code placed} and {@code paid} are the
	 * steps the table placed itself and the ones that used delivered items, so Take Down removes and refunds only those;
	 * records saved before they existed have every step set.
	 */
	private record Built(UUID instanceId, String farmId, long signature, FarmSite site, Map<Item, Item> swaps,
			@Nullable String customName, long finishedAt, @Nullable FarmTemplate layout, BitSet placed, BitSet paid) {
	}

	/**
	 * A player's finished farm, for the My Farms list. {@code id} tells it apart from their other farms in this world;
	 * the rest is what repairing or taking it down needs.
	 */
	public record Finished(String id, Key key, UUID instanceId, UUID ownerUuid, String farmId, long signature, FarmSite site, Map<Item, Item> swaps,
			@Nullable String customName, BlockPos where, Box box, long finishedAt, @Nullable FarmTemplate layout, FarmOperation operation,
			@Nullable RepairLease repairLease, BitSet placed, BitSet paid) {
	}

	private record Area(Box box, @Nullable Built built) {
	}

	public static FarmAreas of(ServerWorld world) {
		return world.getPersistentStateManager().getOrCreate(nbt -> read(nbt, null), FarmAreas::new, STATE_KEY);
	}

	public void claim(Key key, Box box) {
		Area area = new Area(box, null);
		Area previous = areas.put(key, area);
		if (!area.equals(previous)) {
			if (previous != null && previous.built() != null) {
				byInstance = null;
			}
			markDirty();
		}
	}

	/** Keeps a finished farm's area after its build key is released. Recording the same farm twice changes nothing. */
	public void recordFinished(UUID player, FarmTemplate template, FarmSite site, Map<Item, Item> swaps) {
		recordFinished(player, template, site, swaps, UUID.randomUUID(), null);
	}

	/** Records a farm whose placed and paid steps are unknown: every step counts as both. */
	public void recordFinished(UUID player, FarmTemplate template, FarmSite site, Map<Item, Item> swaps, UUID instanceId,
			@Nullable String customName) {
		recordFinished(player, template, site, swaps, instanceId, customName, every(template), every(template));
	}

	/** Records a finished farm with the steps the table placed and the ones it was paid for. Recording the same farm twice changes nothing. */
	public void recordFinished(UUID player, FarmTemplate template, FarmSite site, Map<Item, Item> swaps, UUID instanceId,
			@Nullable String customName, BitSet placed, BitSet paid) {
		Key key = Key.finished(player, site);
		if (!areas.containsKey(key)) {
			String farmId = FarmTemplates.baseId(template.id());
			String layoutId = FarmTemplates.isSaved(template.id()) ? template.id() : FarmTemplates.newSavedId(farmId, template.signature());
			FarmTemplate layout = saved(layoutId, template);
			areas.put(key, new Area(site.footprint(template), new Built(instanceId, FarmTemplates.baseId(template.id()), template.signature(), site, Map.copyOf(swaps),
					customName, System.currentTimeMillis(), layout, (BitSet) placed.clone(), (BitSet) paid.clone())));
			byInstance = null;
			markDirty();
		}
	}

	/** How many saved records could not be read and are being kept as they were. */
	public int unreadableCount() {
		return unreadable.size();
	}

	/**
	 * What this world holds, for {@code /dreamtable status}. Repairs are counted by their lease, and a running Repair also
	 * holds a build claim, so {@code builds} includes them.
	 */
	public record Stats(int finished, int builds, int repairs, int takeDowns, int areas, int unreadable) {
		public Stats plus(Stats other) {
			return new Stats(finished + other.finished, builds + other.builds, repairs + other.repairs, takeDowns + other.takeDowns,
					areas + other.areas, unreadable + other.unreadable);
		}
	}

	public Stats stats() {
		int finished = 0;
		int builds = 0;
		int takeDowns = 0;
		for (Map.Entry<Key, Area> entry : areas.entrySet()) {
			if (entry.getValue().built() != null) {
				finished++;
			} else if (entry.getKey().kind() == Kind.BUILD) {
				builds++;
			} else if (entry.getKey().kind() == Kind.DISMANTLE) {
				takeDowns++;
			}
		}
		return new Stats(finished, builds, repairLeases.size(), takeDowns, areas.size(), unreadable.size());
	}

	/** Whether the player has a finished record at this site, in any state. */
	public boolean recordedAt(UUID player, FarmSite site) {
		return areas.containsKey(Key.finished(player, site));
	}

	public java.util.Optional<Finished> finished(UUID instanceId) {
		Key key = keyOf(instanceId);
		return key == null ? java.util.Optional.empty() : java.util.Optional.of(view(key, areas.get(key)));
	}

	/** Whether a finished farm with this identity is on record. */
	public boolean hasFinished(UUID instanceId) {
		return keyOf(instanceId) != null;
	}

	/**
	 * The record key of a finished farm, from an index built on first use. The index is checked against the record before
	 * it is trusted and rebuilt when it is out of date, so a change to the areas can never make it answer wrongly.
	 */
	private @Nullable Key keyOf(UUID instanceId) {
		for (int attempt = 0; attempt < 2; attempt++) {
			if (byInstance == null) {
				Map<UUID, Key> index = new java.util.HashMap<>();
				for (Map.Entry<Key, Area> entry : areas.entrySet()) {
					if (entry.getValue().built() != null) {
						index.put(entry.getValue().built().instanceId(), entry.getKey());
					}
				}
				byInstance = index;
			}
			Key key = byInstance.get(instanceId);
			if (key == null) {
				return null;
			}
			Area area = areas.get(key);
			if (area != null && area.built() != null && area.built().instanceId().equals(instanceId)) {
				return key;
			}
			byInstance = null;
		}
		return null;
	}

	/** Persisted alert state belongs to the immutable finished-farm identity, not a table position. */
	public java.util.Optional<FarmAlerts> alerts(UUID instanceId) {
		return hasFinished(instanceId) ? java.util.Optional.of(alerts.computeIfAbsent(instanceId, key -> new FarmAlerts())) : java.util.Optional.empty();
	}

	/** Puts back the alert state of a record that was released and restored, so a partial Take Down doesn't erase it. */
	public void restoreAlerts(UUID instanceId, FarmAlerts restored) {
		if (hasFinished(instanceId)) {
			alerts.put(instanceId, restored);
			markDirty();
		}
	}

	/** Records a fresh loaded-world observation and marks the per-world state only when it changed. */
	public FarmAlerts.Transition observeAlert(UUID instanceId, FarmAlertType type, int quantity, long time) {
		FarmAlerts farmAlerts = alerts(instanceId).orElse(null);
		if (farmAlerts == null) {
			return FarmAlerts.Transition.UNCHANGED;
		}
		FarmAlerts.Transition transition = farmAlerts.observe(type, quantity, time);
		if (transition == FarmAlerts.Transition.NEW || transition == FarmAlerts.Transition.UPDATED || transition == FarmAlerts.Transition.RESOLVED) {
			markDirty();
		}
		return transition;
	}

	/** Resolves repair problems when the same repair resumes or completes, retaining their history. */
	public void resolveRepairAlerts(UUID instanceId, long time) {
		observeAlert(instanceId, FarmAlertType.REPAIR_BLOCKED, 0, time);
		observeAlert(instanceId, FarmAlertType.REPAIR_FAILED, 0, time);
	}

	/** Atomically changes a READY farm to REPAIRING and returns the persisted lease. */
	public java.util.Optional<RepairLease> beginRepair(UUID instanceId, UUID actorUuid, BlockPos tablePos, UUID operationId) {
		if (repairLeases.containsKey(instanceId) || !hasFinished(instanceId)) {
			return java.util.Optional.empty();
		}
		RepairLease lease = new RepairLease(operationId, actorUuid, tablePos);
		repairLeases.put(instanceId, lease);
		markDirty();
		return java.util.Optional.of(lease);
	}

	public boolean completeRepair(UUID instanceId, UUID operationId) {
		return endRepair(instanceId, operationId);
	}

	/**
	 * Ends the repair and adds what it placed to the farm's own record. A step the repair placed again takes that
	 * placement's payment, so a free replacement of a paid block doesn't refund; steps it left alone keep theirs.
	 */
	public boolean completeRepair(UUID instanceId, UUID operationId, BitSet placed, BitSet paid) {
		if (!endRepair(instanceId, operationId)) {
			return false;
		}
		mergePlacement(instanceId, placed, paid);
		return true;
	}

	/**
	 * Adds what a repair placed to the farm's record, whether or not its lease is still held: the blocks stand either way.
	 * Does nothing when the farm has no record any more.
	 */
	public void mergePlacement(UUID instanceId, BitSet placed, BitSet paid) {
		updateBuilt(instanceId, built -> {
			BitSet allPlaced = (BitSet) built.placed().clone();
			allPlaced.or(placed);
			BitSet allPaid = (BitSet) built.paid().clone();
			for (int step = placed.nextSetBit(0); step >= 0; step = placed.nextSetBit(step + 1)) {
				allPaid.set(step, paid.get(step));
			}
			return new Built(built.instanceId(), built.farmId(), built.signature(), built.site(), built.swaps(), built.customName(),
					built.finishedAt(), built.layout(), allPlaced, allPaid);
		});
	}

	/** Every step of the design set, the record of a farm whose placed and paid steps were never saved. */
	private static BitSet every(@Nullable FarmTemplate template) {
		BitSet all = new BitSet();
		if (template != null) {
			all.set(0, template.steps().size());
		}
		return all;
	}

	public boolean failRepair(UUID instanceId, UUID operationId) {
		return endRepair(instanceId, operationId);
	}

	private boolean endRepair(UUID instanceId, UUID operationId) {
		RepairLease lease = repairLeases.get(instanceId);
		if (lease == null || !lease.operationId().equals(operationId)) {
			return false;
		}
		repairLeases.remove(instanceId);
		markDirty();
		return true;
	}

	/** Fails only leases the caller has positively determined no longer have a live table session. */
	public int failOrphanedRepairs(Predicate<RepairLease> active) {
		int before = repairLeases.size();
		repairLeases.values().removeIf(lease -> !active.test(lease));
		int removed = before - repairLeases.size();
		if (removed > 0) {
			markDirty();
		}
		return removed;
	}

	/** An unloaded table is unknown, never orphaned; a loaded missing table or matching session proves the outcome. */
	public int reconcileRepairs(ServerWorld world) {
		int removed = 0;
		Iterator<Map.Entry<UUID, RepairLease>> leases = repairLeases.entrySet().iterator();
		while (leases.hasNext()) {
			Map.Entry<UUID, RepairLease> entry = leases.next();
			RepairLease lease = entry.getValue();
			if (world.isChunkLoaded(lease.tablePos()) && (!(world.getBlockEntity(lease.tablePos()) instanceof DreamTableBlockEntity table)
					|| !table.hasRepairLease(entry.getKey(), lease))) {
				leases.remove();
				removed++;
			}
		}
		if (removed > 0) {
			markDirty();
		}
		return removed;
	}

	public boolean repairActive(UUID instanceId, RepairLease expected) {
		return expected.equals(repairLeases.get(instanceId));
	}

	private void updateBuilt(UUID instanceId, UnaryOperator<Built> operation) {
		Key key = keyOf(instanceId);
		if (key == null) {
			return;
		}
		Area area = areas.get(key);
		Built changed = operation.apply(area.built());
		if (!changed.equals(area.built())) {
			areas.put(key, new Area(area.box(), changed));
			markDirty();
		}
	}

	public void release(Key key) {
		Area removed = areas.remove(key);
		if (removed != null) {
			if (removed.built() != null) {
				byInstance = null;
				FarmLoader.requestRenewal();
			}
			if (removed.built() != null) {
				repairLeases.remove(removed.built().instanceId());
				alerts.remove(removed.built().instanceId());
			}
			markDirty();
		}
	}

	/** A broken table frees its builds and take-aparts. Its finished farms are still standing, so they stay. */
	public void releaseTable(BlockPos table) {
		if (areas.keySet().removeIf(key -> key.table() == table.asLong() && key.kind() != Kind.FINISHED)) {
			byInstance = null;
			markDirty();
		}
	}

	/** What this area would overlap, other than the asker's own farm. Finished farms that were taken down are forgotten here. */
	public Conflict conflict(ServerWorld world, Key asker, Box box) {
		return conflict(world, asker, box, null);
	}

	public Conflict conflict(ServerWorld world, Key asker, Box box, @Nullable UUID ignoredFinished) {
		Conflict found = Conflict.NONE;
		Iterator<Map.Entry<Key, Area>> entries = areas.entrySet().iterator();
		while (entries.hasNext()) {
			Map.Entry<Key, Area> entry = entries.next();
			if (entry.getKey().equals(asker) || ignored(entry.getValue(), ignoredFinished) || !entry.getValue().box().intersects(box)) {
				continue;
			}
			if (forgotten(world, entry.getKey(), entry.getValue())) {
				entries.remove();
				byInstance = null;
				markDirty();
				continue;
			}
			if (entry.getKey().kind() != Kind.FINISHED) {
				return Conflict.ACTIVE;
			}
			found = Conflict.FINISHED;
		}
		if (found == Conflict.NONE && unreadableCovers(other -> other.intersects(box))) {
			return Conflict.FINISHED;
		}
		return found;
	}

	public List<Finished> finishedBy(UUID player) {
		List<Finished> farms = new ArrayList<>();
		for (Map.Entry<Key, Area> entry : areas.entrySet()) {
			if (entry.getValue().built() != null && entry.getKey().player().equals(player)) {
				farms.add(view(entry.getKey(), entry.getValue()));
			}
		}
		return farms;
	}

	public List<Finished> finished() {
		List<Finished> farms = new ArrayList<>();
		for (Map.Entry<Key, Area> entry : areas.entrySet()) {
			if (entry.getValue().built() != null) {
				farms.add(view(entry.getKey(), entry.getValue()));
			}
		}
		return farms;
	}

	/**
	 * A finished record as the rest of the mod sees it. Its {@code placed} and {@code paid} sets are the record's own and
	 * are never changed once stored, so they are shared instead of copied: callers must treat them as read-only.
	 */
	private Finished view(Key key, Area area) {
		Built built = area.built();
		BlockPos where = built.site().anchor() != null ? built.site().anchor() : built.site().table();
		RepairLease lease = repairLeases.get(built.instanceId());
		return new Finished(key.table() + "/" + key.spot(), key, built.instanceId(), key.player(), built.farmId(), built.signature(), built.site(), built.swaps(),
				built.customName(), where, area.box(), built.finishedAt(), built.layout(), lease == null ? FarmOperation.READY : FarmOperation.REPAIRING,
				lease, built.placed(), built.paid());
	}

	/** What block protection needs from a finished farm: who owns it and its design at its site. */
	public record Guard(UUID instanceId, UUID ownerUuid, FarmTemplate layout, FarmSite site, Map<Item, Item> swaps) {
	}

	/**
	 * The finished farms with a saved design whose area holds the position. One box test per area and nothing is copied
	 * for the farms that don't match, so it is cheap enough to ask for every block a player breaks.
	 */
	public List<Guard> guardsAt(BlockPos pos) {
		List<Guard> guards = List.of();
		for (Map.Entry<Key, Area> entry : areas.entrySet()) {
			Area area = entry.getValue();
			Built built = area.built();
			if (built == null || built.layout() == null || !area.box().contains(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)) {
				continue;
			}
			if (guards.isEmpty()) {
				guards = new ArrayList<>(1);
			}
			guards.add(new Guard(built.instanceId(), entry.getKey().player(), built.layout(), built.site(), built.swaps()));
		}
		return guards;
	}

	/** An area as shown to players who turned on "Show Other Farms". */
	public record View(Box box, Kind kind) {
	}

	/** Other players' areas whose middle is within {@code radius} blocks. */
	public List<View> near(BlockPos center, int radius, UUID viewer) {
		Vec3d from = Vec3d.ofCenter(center);
		List<View> views = new ArrayList<>();
		for (Map.Entry<Key, Area> entry : areas.entrySet()) {
			Box box = entry.getValue().box();
			if (!entry.getKey().player().equals(viewer) && box.getCenter().squaredDistanceTo(from) <= (double) radius * radius) {
				views.add(new View(box, entry.getKey().kind()));
			}
		}
		return views;
	}

	/** True when the spot lies inside a farm other than the asker's own. Cheap enough to ask for every block. */
	public boolean reservedByOther(ServerWorld world, Key asker, BlockPos pos) {
		return reservedByOther(world, asker, pos, null);
	}

	public boolean reservedByOther(ServerWorld world, Key asker, BlockPos pos, @Nullable UUID ignoredFinished) {
		return find(world, asker, ignoredFinished, other -> other.contains(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
	}

	/**
	 * True when a build, a take-apart or a finished farm of any player covers the spot, so a Dream Table can't be placed there:
	 * a table inside a farm would be guarded by no record of its own.
	 */
	public boolean reserved(ServerWorld world, BlockPos pos) {
		return find(world, null, null, area -> area.contains(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
	}

	/** Whether any area other than the asker's (none when {@code asker} is null) and not ignored satisfies {@code test}. */
	private boolean find(ServerWorld world, @Nullable Key asker, @Nullable UUID ignoredFinished, Predicate<Box> test) {
		Iterator<Map.Entry<Key, Area>> entries = areas.entrySet().iterator();
		while (entries.hasNext()) {
			Map.Entry<Key, Area> entry = entries.next();
			if (entry.getKey().equals(asker) || ignored(entry.getValue(), ignoredFinished)) {
				continue;
			}
			if (entry.getKey().kind() != Kind.FINISHED && tableGone(world, entry.getKey())) {
				entries.remove();
				byInstance = null;
				markDirty();
				continue;
			}
			if (test.test(entry.getValue().box())) {
				return true;
			}
		}
		// A table's own held build never blocks its own farm: its owner resumes it, and FarmBuilder.check asks here before the claim.
		ParkedProjects.Seat own = asker == null ? null : new ParkedProjects.Seat(asker.player(), world.getRegistryKey().getValue(), asker.table());
		return ParkedProjects.of(world.getServer()).reserves(world.getRegistryKey().getValue(), test, own) || unreadableCovers(test);
	}

	/**
	 * Whether a record that could not be read covers a box the test accepts. Only its saved box is known, and that is still
	 * the area the farm holds, so it is kept as saved and checked as the areas are.
	 */
	boolean unreadableCovers(Predicate<Box> test) {
		for (NbtCompound raw : unreadable) {
			int[] box = raw.getIntArray("Box");
			if (box.length == 6 && test.test(new Box(box[0], box[1], box[2], box[3], box[4], box[5]))) {
				return true;
			}
		}
		return false;
	}

	private static boolean ignored(Area area, @Nullable UUID ignoredFinished) {
		return ignoredFinished != null && area.built() != null && area.built().instanceId().equals(ignoredFinished);
	}

	private boolean forgotten(ServerWorld world, Key key, Area area) {
		if (key.kind() != Kind.FINISHED) {
			return tableGone(world, key);
		}
		Built built = area.built();
		return built == null || (!repairLeases.containsKey(built.instanceId()) && !standing(world, built));
	}

	/** Only a loaded chunk can prove the table is gone; an unloaded table keeps its area. */
	private static boolean tableGone(ServerWorld world, Key key) {
		BlockPos table = BlockPos.fromLong(key.table());
		return world.isChunkLoaded(table) && !(world.getBlockEntity(table) instanceof DreamTableBlockEntity);
	}

	/**
	 * A finished farm counts as standing while at least half of its blocks are still there as built.
	 * Unloaded parts can't be checked, so they count as standing; a farm whose file is gone or changed can't be checked and is let go.
	 */
	private static boolean standing(ServerWorld world, @Nullable Built built) {
		FarmTemplate template = built == null ? null : built.layout();
		if (template == null) {
			return false;
		}
		Presence presence = presence(world, template, built.site(), built.swaps());
		return presence.unloaded() || presence.percent() >= 50;
	}

	/**
	 * How much of a farm already stands at a site, counting only loaded blocks: the share still as built, the spots
	 * left empty, which a repair fills, with the items that takes, and the spots where the player put another block,
	 * which a repair leaves alone.
	 */
	public record Presence(int percent, int missing, int changed, boolean unloaded, Map<Item, Integer> needs) {
	}

	/** What a farm spot holds compared with the farm's block. */
	enum Spot {
		/** Still the farm's block, as Discard sees it ({@link BlockMatch#stillFarm}). */
		FARM,
		/** Empty, or something any block replaces, like air, flowing water or grass. */
		MISSING,
		/** Another solid block, put there by the player or the world. */
		CHANGED
	}

	static Spot spot(BlockState current, BlockState expected) {
		if (BlockMatch.stillFarm(current, expected)) {
			return Spot.FARM;
		}
		return current.isReplaceable() ? Spot.MISSING : Spot.CHANGED;
	}

	/** Compares every block of the farm with the world. */
	public static Presence presence(ServerWorld world, FarmTemplate template, FarmSite site, Map<Item, Item> swaps) {
		int blocks = 0;
		int found = 0;
		int missing = 0;
		boolean unloaded = false;
		Map<Item, Integer> needs = new LinkedHashMap<>();
		BitSet growth = FarmGrowth.growth(template);
		for (int index = 0; index < template.steps().size(); index++) {
			FarmStep step = template.steps().get(index);
			if (step.isEntity() || step.state() == null || growth.get(index)) {
				continue;
			}
			BlockPos pos = site.target(template, step);
			if (!world.isChunkLoaded(pos)) {
				unloaded = true;
				continue;
			}
			BlockState expected = site.orient(MaterialSwaps.apply(step.state(), swaps));
			blocks++;
			switch (spot(world.getBlockState(pos), expected)) {
				case FARM -> found++;
				case MISSING -> {
					missing++;
					if (step.item() != null) {
						needs.merge(swaps.getOrDefault(step.item(), step.item()), 1, Integer::sum);
					}
				}
				case CHANGED -> {
				}
			}
		}
		return new Presence(blocks == 0 ? 0 : found * 100 / blocks, missing, blocks - found - missing, unloaded, needs);
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt) {
		NbtList list = new NbtList();
		for (Map.Entry<Key, Area> entry : areas.entrySet()) {
			NbtCompound item = new NbtCompound();
			Key key = entry.getKey();
			item.putLong("Table", key.table());
			item.putUuid("Player", key.player());
			item.putString("Kind", key.kind().name());
			item.putLong("Spot", key.spot());
			Box box = entry.getValue().box();
			item.putIntArray("Box", new int[]{(int) box.minX, (int) box.minY, (int) box.minZ, (int) box.maxX, (int) box.maxY, (int) box.maxZ});
			Built built = entry.getValue().built();
			if (built != null) {
				item.putUuid("InstanceId", built.instanceId());
				item.putString("Farm", built.farmId());
				item.putLong("FarmSignature", built.signature());
				NbtCompound site = new NbtCompound();
				built.site().write(site);
				item.put("Site", site);
				MaterialLedger.writeSwaps(item, built.swaps());
				if (built.customName() != null) {
					item.putString("CustomName", built.customName());
				}
				RepairLease lease = repairLeases.get(built.instanceId());
				if (lease != null) {
					lease.write(item);
				}
				FarmAlerts farmAlerts = alerts.get(built.instanceId());
				if (farmAlerts != null) {
					item.put("Alerts", farmAlerts.write(new NbtCompound()));
				}
				item.putLong("FinishedAt", built.finishedAt());
				if (built.layout() != null) {
					item.putString("LayoutId", built.layout().id());
					item.put("Layout", ENCODED_LAYOUTS.get(built.layout(), layout -> TemplateCodec.encode(layout, true)).copy());
				}
				item.putLongArray("PlacedSteps", built.placed().toLongArray());
				item.putLongArray("PaidSteps", built.paid().toLongArray());
			}
			list.add(item);
		}
		for (NbtCompound raw : unreadable) {
			list.add(raw.copy());
		}
		nbt.put("Areas", list);
		return nbt;
	}

	static FarmAreas read(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		FarmAreas state = new FarmAreas();
		Set<UUID> instanceIds = new HashSet<>();
		NbtList list = nbt.getList("Areas", NbtElement.COMPOUND_TYPE);
		for (int index = 0; index < list.size(); index++) {
			NbtCompound item = list.getCompound(index);
			int[] box = item.getIntArray("Box");
			if (box.length != 6 || !item.containsUuid("Player")) {
				TheDreamTableMod.LOGGER.error("Keeping farm area record #{} unchanged: it has no box or player", index);
				state.unreadable.add(item.copy());
				continue;
			}
			try {
				Kind kind = kindOf(item);
				Built built = kind == Kind.FINISHED ? readBuilt(state, item, instanceIds) : null;
				Key key = new Key(item.getLong("Table"), item.getUuid("Player"), kind, item.getLong("Spot"));
				state.areas.put(key, new Area(new Box(box[0], box[1], box[2], box[3], box[4], box[5]), built));
			} catch (RuntimeException corrupt) {
				// One malformed record must not discard the dimension's other farms, nor be deleted by the next save.
				TheDreamTableMod.LOGGER.error("Keeping unreadable farm area record #{} unchanged", index, corrupt);
				state.unreadable.add(item.copy());
				state.markDirty();
			}
		}
		return state;
	}

	/**
	 * A finished farm's record. Farms saved before designs were kept get theirs from the current farm when it still
	 * matches, and are saved again with it.
	 */
	private static Built readBuilt(FarmAreas state, NbtCompound item, Set<UUID> instanceIds) {
		UUID instanceId = item.containsUuid("InstanceId") ? item.getUuid("InstanceId") : null;
		if (instanceId == null || !instanceIds.add(instanceId)) {
			int salt = 0;
			do {
				String seed = item.getUuid("Player") + ":" + item.getLong("Table") + ":" + item.getLong("Spot") + ":" + salt++;
				instanceId = UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
			} while (!instanceIds.add(instanceId));
			state.markDirty();
		}
		String farmId = FarmTemplates.baseId(item.getString("Farm"));
		long signature = item.getLong("FarmSignature");
		FarmTemplate layout = null;
		if (item.contains("Layout", NbtElement.COMPOUND_TYPE)) {
			String layoutId = item.contains("LayoutId", NbtElement.STRING_TYPE)
					? item.getString("LayoutId") : FarmTemplates.newSavedId(farmId, signature);
			layout = TemplateCodec.decode(layoutId, item.getCompound("Layout"));
			FarmTemplates.keepSaved(layout);
			if (!item.contains("LayoutId", NbtElement.STRING_TYPE)) {
				state.markDirty();
			}
		} else {
			FarmTemplate current = FarmTemplates.get(farmId);
			if (current != null && current.signature() == signature) {
				layout = saved(FarmTemplates.newSavedId(farmId, signature), current);
				state.markDirty();
			}
		}
		String customName = null;
		if (item.contains("CustomName", NbtElement.STRING_TYPE)) {
			FarmNames.Validation validation = FarmNames.validate(item.getString("CustomName"));
			if (validation.result() == FarmNames.Result.VALID) {
				customName = validation.name();
			} else {
				state.markDirty();
			}
		}
		RepairLease lease = RepairLease.read(item);
		if (lease != null) {
			state.repairLeases.put(instanceId, lease);
		}
		if (item.contains("Alerts", NbtElement.COMPOUND_TYPE)) {
			state.alerts.put(instanceId, FarmAlerts.read(item.getCompound("Alerts")));
		}
		boolean steps = item.contains("PlacedSteps", NbtElement.LONG_ARRAY_TYPE) && item.contains("PaidSteps", NbtElement.LONG_ARRAY_TYPE);
		BitSet placed = steps ? BitSet.valueOf(item.getLongArray("PlacedSteps")) : every(layout);
		BitSet paid = steps ? BitSet.valueOf(item.getLongArray("PaidSteps")) : every(layout);
		// Collaborator, invite and payer data saved by earlier versions is not read and is dropped on the next save.
		return new Built(instanceId, farmId, signature, FarmSite.read(item.getCompound("Site")), MaterialLedger.readSwaps(item), customName,
				item.getLong("FinishedAt"), layout, placed, paid);
	}

	/** A copy of the design under its saved id, kept in {@link FarmTemplates} so builds and take-aparts can find it. */
	private static FarmTemplate saved(String id, FarmTemplate template) {
		FarmTemplate layout = template.id().equals(id) ? template : new FarmTemplate(id, template.steps(), template.materials(), template.center());
		FarmTemplates.keepSaved(layout);
		return layout;
	}

	/** Areas saved by 1.1.3 only had a Dismantling flag. */
	private static Kind kindOf(NbtCompound item) {
		if (item.contains("Kind", NbtElement.STRING_TYPE)) {
			for (Kind kind : Kind.values()) {
				if (kind.name().equals(item.getString("Kind"))) {
					return kind;
				}
			}
		}
		return item.getBoolean("Dismantling") ? Kind.DISMANTLE : Kind.BUILD;
	}
}
