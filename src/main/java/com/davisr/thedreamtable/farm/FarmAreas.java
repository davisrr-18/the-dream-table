package com.davisr.thedreamtable.farm;

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
	private static final Type<FarmAreas> TYPE = new Type<>(FarmAreas::new, FarmAreas::read, null);
	private final Map<Key, Area> areas = new LinkedHashMap<>();
	private final Map<UUID, RepairLease> repairLeases = new LinkedHashMap<>();
	private final Map<UUID, FarmAlerts> alerts = new LinkedHashMap<>();

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
	 * only for farms finished by an older version whose design has changed since.
	 */
	private record Built(UUID instanceId, String farmId, long signature, FarmSite site, Map<Item, Item> swaps,
			@Nullable String customName, Map<UUID, FarmGrant> grants, Map<UUID, FarmInvite> invites, long finishedAt,
			@Nullable FarmTemplate layout) {
	}

	/**
	 * A player's finished farm, for the My Farms list. {@code id} tells it apart from their other farms in this world;
	 * the rest is what repairing or taking it down needs.
	 */
	public record Finished(String id, Key key, UUID instanceId, UUID ownerUuid, String farmId, long signature, FarmSite site, Map<Item, Item> swaps,
			@Nullable String customName, Map<UUID, FarmGrant> grants, Map<UUID, FarmInvite> invites, BlockPos where, Box box, long finishedAt,
			@Nullable FarmTemplate layout, FarmOperation operation, @Nullable RepairLease repairLease) {
	}

	private record Area(Box box, @Nullable Built built) {
	}

	public static FarmAreas of(ServerWorld world) {
		return world.getPersistentStateManager().getOrCreate(TYPE, "the_dream_table_farm_areas");
	}

	public void claim(Key key, Box box) {
		Area area = new Area(box, null);
		if (!area.equals(areas.put(key, area))) {
			markDirty();
		}
	}

	/** Keeps a finished farm's area after its build key is released. Recording the same farm twice changes nothing. */
	public void recordFinished(UUID player, FarmTemplate template, FarmSite site, Map<Item, Item> swaps) {
		recordFinished(player, template, site, swaps, UUID.randomUUID(), null, Map.of(), Map.of());
	}

	/** Restores a finished record after Repair without changing its public identity or accepted collaborators. */
	public void recordFinished(UUID player, FarmTemplate template, FarmSite site, Map<Item, Item> swaps, UUID instanceId,
			Map<UUID, FarmGrant> grants) {
		recordFinished(player, template, site, swaps, instanceId, null, grants, Map.of());
	}

	/** Restores all owner-managed metadata after an operation that temporarily reconstructs the finished record. */
	public void recordFinished(UUID player, FarmTemplate template, FarmSite site, Map<Item, Item> swaps, UUID instanceId,
			@Nullable String customName, Map<UUID, FarmGrant> grants, Map<UUID, FarmInvite> invites) {
		Key key = Key.finished(player, site);
		if (!areas.containsKey(key)) {
			String farmId = FarmTemplates.baseId(template.id());
			String layoutId = FarmTemplates.isSaved(template.id()) ? template.id() : FarmTemplates.newSavedId(farmId, template.signature());
			FarmTemplate layout = saved(layoutId, template);
			Map<UUID, FarmGrant> collaborators = new LinkedHashMap<>(grants);
			collaborators.remove(player);
			Map<UUID, FarmInvite> pending = new LinkedHashMap<>(invites);
			pending.values().removeIf(invite -> invite.playerUuid().equals(player));
			areas.put(key, new Area(site.footprint(template), new Built(instanceId, FarmTemplates.baseId(template.id()), template.signature(), site, Map.copyOf(swaps),
					customName, Map.copyOf(collaborators), Map.copyOf(pending), System.currentTimeMillis(), layout)));
			markDirty();
		}
	}

	public java.util.Optional<Finished> finished(UUID instanceId) {
		return finished().stream().filter(farm -> farm.instanceId().equals(instanceId)).findFirst();
	}

	/** Persisted alert state belongs to the immutable finished-farm identity, not a table position. */
	public java.util.Optional<FarmAlerts> alerts(UUID instanceId) {
		return finished(instanceId).map(ignored -> alerts.computeIfAbsent(instanceId, key -> new FarmAlerts()));
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
		if (repairLeases.containsKey(instanceId) || finished(instanceId).isEmpty()) {
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

	void setCustomName(UUID instanceId, @Nullable String customName) {
		updateBuilt(instanceId, built -> new Built(built.instanceId(), built.farmId(), built.signature(), built.site(), built.swaps(),
				customName, built.grants(), built.invites(), built.finishedAt(), built.layout()));
	}

	void putGrant(UUID instanceId, FarmGrant grant) {
		updateBuilt(instanceId, built -> {
			Map<UUID, FarmGrant> grants = new LinkedHashMap<>(built.grants());
			grants.put(grant.playerUuid(), grant);
			return new Built(built.instanceId(), built.farmId(), built.signature(), built.site(), built.swaps(), built.customName(),
					Map.copyOf(grants), built.invites(), built.finishedAt(), built.layout());
		});
	}

	void putInvite(UUID instanceId, FarmInvite invite) {
		updateBuilt(instanceId, built -> {
			Map<UUID, FarmInvite> invites = new LinkedHashMap<>(built.invites());
			invites.put(invite.inviteId(), invite);
			return new Built(built.instanceId(), built.farmId(), built.signature(), built.site(), built.swaps(), built.customName(),
					built.grants(), Map.copyOf(invites), built.finishedAt(), built.layout());
		});
	}

	void acceptInvite(UUID instanceId, UUID inviteId, FarmGrant grant) {
		updateBuilt(instanceId, built -> {
			if (!built.invites().containsKey(inviteId)) {
				return built;
			}
			Map<UUID, FarmInvite> invites = new LinkedHashMap<>(built.invites());
			invites.remove(inviteId);
			Map<UUID, FarmGrant> grants = new LinkedHashMap<>(built.grants());
			grants.put(grant.playerUuid(), grant);
			return new Built(built.instanceId(), built.farmId(), built.signature(), built.site(), built.swaps(), built.customName(),
					Map.copyOf(grants), Map.copyOf(invites), built.finishedAt(), built.layout());
		});
	}

	public boolean removeInvite(UUID instanceId, UUID inviteId, UUID invitedPlayer) {
		boolean[] removed = {false};
		updateBuilt(instanceId, built -> {
			FarmInvite invite = built.invites().get(inviteId);
			if (invite == null || !invite.playerUuid().equals(invitedPlayer)) {
				return built;
			}
			Map<UUID, FarmInvite> invites = new LinkedHashMap<>(built.invites());
			invites.remove(inviteId);
			removed[0] = true;
			return new Built(built.instanceId(), built.farmId(), built.signature(), built.site(), built.swaps(), built.customName(),
					built.grants(), Map.copyOf(invites), built.finishedAt(), built.layout());
		});
		return removed[0];
	}

	public boolean removeGrant(UUID instanceId, UUID playerUuid) {
		boolean[] removed = {false};
		updateBuilt(instanceId, built -> {
			if (!built.grants().containsKey(playerUuid)) {
				return built;
			}
			Map<UUID, FarmGrant> grants = new LinkedHashMap<>(built.grants());
			grants.remove(playerUuid);
			removed[0] = true;
			return new Built(built.instanceId(), built.farmId(), built.signature(), built.site(), built.swaps(), built.customName(),
					Map.copyOf(grants), built.invites(), built.finishedAt(), built.layout());
		});
		return removed[0];
	}

	int removeExpiredInvites(long now) {
		int removed = 0;
		for (Map.Entry<Key, Area> entry : areas.entrySet()) {
			Built built = entry.getValue().built();
			if (built == null || built.invites().isEmpty()) {
				continue;
			}
			Map<UUID, FarmInvite> invites = new LinkedHashMap<>(built.invites());
			int before = invites.size();
			invites.values().removeIf(invite -> invite.expired(now));
			if (invites.size() != before) {
				removed += before - invites.size();
				Built changed = new Built(built.instanceId(), built.farmId(), built.signature(), built.site(), built.swaps(), built.customName(),
						built.grants(), Map.copyOf(invites), built.finishedAt(), built.layout());
				entry.setValue(new Area(entry.getValue().box(), changed));
			}
		}
		if (removed > 0) {
			markDirty();
		}
		return removed;
	}

	int pendingInvites(UUID playerUuid, long now) {
		int count = 0;
		for (Area area : areas.values()) {
			Built built = area.built();
			if (built == null) {
				continue;
			}
			for (FarmInvite invite : built.invites().values()) {
				if (invite.playerUuid().equals(playerUuid) && !invite.expired(now)) {
					count++;
				}
			}
		}
		return count;
	}

	private void updateBuilt(UUID instanceId, UnaryOperator<Built> operation) {
		for (Map.Entry<Key, Area> entry : areas.entrySet()) {
			Built built = entry.getValue().built();
			if (built != null && built.instanceId().equals(instanceId)) {
				Built changed = operation.apply(built);
				if (!changed.equals(built)) {
					entry.setValue(new Area(entry.getValue().box(), changed));
					markDirty();
				}
				return;
			}
		}
	}

	public void release(Key key) {
		Area removed = areas.remove(key);
		if (removed != null) {
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
				markDirty();
				continue;
			}
			if (entry.getKey().kind() != Kind.FINISHED) {
				return Conflict.ACTIVE;
			}
			found = Conflict.FINISHED;
		}
		return found;
	}

	public List<Finished> finishedBy(UUID player) {
		return finished().stream().filter(farm -> farm.ownerUuid().equals(player)).toList();
	}

	public List<Finished> finished() {
		List<Finished> farms = new ArrayList<>();
		for (Map.Entry<Key, Area> entry : areas.entrySet()) {
			Built built = entry.getValue().built();
			if (built != null) {
				Key key = entry.getKey();
				BlockPos where = built.site().anchor() != null ? built.site().anchor() : built.site().table();
				RepairLease lease = repairLeases.get(built.instanceId());
				farms.add(new Finished(key.table() + "/" + key.spot(), key, built.instanceId(), key.player(), built.farmId(), built.signature(), built.site(), built.swaps(), built.customName(), built.grants(), built.invites(), where,
						entry.getValue().box(), built.finishedAt(), built.layout(), lease == null ? FarmOperation.READY : FarmOperation.REPAIRING, lease));
			}
		}
		return farms;
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

	private boolean find(ServerWorld world, Key asker, @Nullable UUID ignoredFinished, Predicate<Box> test) {
		Iterator<Map.Entry<Key, Area>> entries = areas.entrySet().iterator();
		while (entries.hasNext()) {
			Map.Entry<Key, Area> entry = entries.next();
			if (entry.getKey().equals(asker) || ignored(entry.getValue(), ignoredFinished)) {
				continue;
			}
			if (entry.getKey().kind() != Kind.FINISHED && tableGone(world, entry.getKey())) {
				entries.remove();
				markDirty();
				continue;
			}
			if (test.test(entry.getValue().box())) {
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
		for (int index = 0; index < template.steps().size(); index++) {
			FarmStep step = template.steps().get(index);
			if (step.isEntity() || step.state() == null || FarmGrowth.isGrowth(template, index)) {
				continue;
			}
			BlockPos pos = site.target(template, step);
			if (!world.isChunkLoaded(pos)) {
				unloaded = true;
				continue;
			}
			BlockState expected = MaterialSwaps.apply(step.state(), swaps).rotate(FarmGeometry.rotation(site.facing()));
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
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
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
				FarmGrant.writeAll(item, "Grants", built.grants());
				FarmInvite.writeAll(item, "Invites", built.invites());
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
					item.put("Layout", TemplateCodec.encode(built.layout(), true));
				}
			}
			list.add(item);
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
				continue;
			}
			Kind kind = kindOf(item);
			Built built = kind == Kind.FINISHED ? readBuilt(state, item, instanceIds) : null;
			Key key = new Key(item.getLong("Table"), item.getUuid("Player"), kind, item.getLong("Spot"));
			state.areas.put(key, new Area(new Box(box[0], box[1], box[2], box[3], box[4], box[5]), built));
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
		Map<UUID, FarmGrant> grants = new LinkedHashMap<>(FarmGrant.readAll(item, "Grants"));
		grants.remove(item.getUuid("Player"));
		Map<UUID, FarmInvite> invites = readInvites(item, item.getUuid("Player"));
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
		return new Built(instanceId, farmId, signature, FarmSite.read(item.getCompound("Site")), MaterialLedger.readSwaps(item), customName,
				Map.copyOf(grants), invites, item.getLong("FinishedAt"), layout);
	}

	private static Map<UUID, FarmInvite> readInvites(NbtCompound item, UUID ownerUuid) {
		Map<UUID, FarmInvite> invites = new LinkedHashMap<>(FarmInvite.readAll(item, "Invites"));
		invites.values().removeIf(invite -> invite.playerUuid().equals(ownerUuid));
		return Map.copyOf(invites);
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
