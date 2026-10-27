package com.davisr.thedreamtable.farm;

import com.davisr.thedreamtable.block.entity.DreamTableBlockEntity;
import com.davisr.thedreamtable.block.entity.MaterialLedger;
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

/**
 * The areas of every farm in a world, across all tables: farms being built, farms being taken apart, and finished farms.
 * A new farm can't overlap any of them, and a build never clears blocks inside another one.
 * Build and take-apart areas go away with their table. A finished farm stands on its own, so its area stays even
 * without the table, until less than half of its blocks are left, which is checked when a new farm asks to overlap it.
 */
public final class FarmAreas extends PersistentState {
	private static final Type<FarmAreas> TYPE = new Type<>(FarmAreas::new, FarmAreas::read, null);

	private final Map<Key, Area> areas = new LinkedHashMap<>();

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
			Map<UUID, FarmGrant> grants, long finishedAt, @Nullable FarmTemplate layout) {
	}

	/**
	 * A player's finished farm, for the My Farms list. {@code id} tells it apart from their other farms in this world;
	 * the rest is what repairing or taking it down needs.
	 */
	public record Finished(String id, Key key, UUID instanceId, UUID ownerUuid, String farmId, long signature, FarmSite site, Map<Item, Item> swaps,
			Map<UUID, FarmGrant> grants, BlockPos where, Box box, long finishedAt,
			@Nullable FarmTemplate layout) {
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
		recordFinished(player, template, site, swaps, UUID.randomUUID(), Map.of());
	}

	/** Restores a finished record after Repair without changing its public identity or accepted collaborators. */
	public void recordFinished(UUID player, FarmTemplate template, FarmSite site, Map<Item, Item> swaps, UUID instanceId,
			Map<UUID, FarmGrant> grants) {
		Key key = Key.finished(player, site);
		if (!areas.containsKey(key)) {
			String farmId = FarmTemplates.baseId(template.id());
			String layoutId = FarmTemplates.isSaved(template.id()) ? template.id() : FarmTemplates.newSavedId(farmId, template.signature());
			FarmTemplate layout = saved(layoutId, template);
			Map<UUID, FarmGrant> collaborators = new LinkedHashMap<>(grants);
			collaborators.remove(player);
			areas.put(key, new Area(site.footprint(template), new Built(instanceId, FarmTemplates.baseId(template.id()), template.signature(), site, Map.copyOf(swaps),
					Map.copyOf(collaborators), System.currentTimeMillis(), layout)));
			markDirty();
		}
	}

	public void release(Key key) {
		if (areas.remove(key) != null) {
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
		Conflict found = Conflict.NONE;
		Iterator<Map.Entry<Key, Area>> entries = areas.entrySet().iterator();
		while (entries.hasNext()) {
			Map.Entry<Key, Area> entry = entries.next();
			if (entry.getKey().equals(asker) || !entry.getValue().box().intersects(box)) {
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
				farms.add(new Finished(key.table() + "/" + key.spot(), key, built.instanceId(), key.player(), built.farmId(), built.signature(), built.site(), built.swaps(), built.grants(), where,
						entry.getValue().box(), built.finishedAt(), built.layout()));
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
		return find(world, asker, other -> other.contains(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
	}

	private boolean find(ServerWorld world, Key asker, Predicate<Box> test) {
		Iterator<Map.Entry<Key, Area>> entries = areas.entrySet().iterator();
		while (entries.hasNext()) {
			Map.Entry<Key, Area> entry = entries.next();
			if (entry.getKey().equals(asker)) {
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

	private static boolean forgotten(ServerWorld world, Key key, Area area) {
		return key.kind() == Kind.FINISHED ? !standing(world, area.built()) : tableGone(world, key);
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
				FarmGrant.writeAll(item, "Grants", built.grants());
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
		return new Built(instanceId, farmId, signature, FarmSite.read(item.getCompound("Site")), MaterialLedger.readSwaps(item), Map.copyOf(grants),
				item.getLong("FinishedAt"), layout);
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
