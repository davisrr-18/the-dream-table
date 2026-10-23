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
import net.minecraft.world.PersistentState;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
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

		public static Key dismantle(BlockPos table, UUID player) {
			return new Key(table.asLong(), player, Kind.DISMANTLE, 0L);
		}

		static Key finished(UUID player, FarmSite site) {
			BlockPos origin = site.anchor() != null ? site.anchor() : site.table();
			return new Key(site.table().asLong(), player, Kind.FINISHED, origin.asLong() * 4 + site.facing().getHorizontal());
		}
	}

	/** A finished farm remembers what it built, so it can tell whether it is still standing. */
	private record Built(String farmId, long signature, FarmSite site, Map<Item, Item> swaps) {
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
		Key key = Key.finished(player, site);
		if (!areas.containsKey(key)) {
			areas.put(key, new Area(site.footprint(template), new Built(template.id(), template.signature(), site, Map.copyOf(swaps))));
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
		FarmTemplate template = built == null ? null : FarmTemplates.get(built.farmId());
		if (template == null || template.signature() != built.signature()) {
			return false;
		}
		int blocks = 0;
		int found = 0;
		for (FarmStep step : template.steps()) {
			if (step.isEntity() || step.state() == null) {
				continue;
			}
			BlockPos pos = built.site().target(template, step);
			if (!world.isChunkLoaded(pos)) {
				return true;
			}
			BlockState expected = MaterialSwaps.apply(step.state(), built.swaps()).rotate(FarmGeometry.rotation(built.site().facing()));
			blocks++;
			if (BlockMatch.sameShape(world.getBlockState(pos), expected)) {
				found++;
			}
		}
		return found * 2 >= blocks;
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
				item.putString("Farm", built.farmId());
				item.putLong("FarmSignature", built.signature());
				NbtCompound site = new NbtCompound();
				built.site().write(site);
				item.put("Site", site);
				MaterialLedger.writeSwaps(item, built.swaps());
			}
			list.add(item);
		}
		nbt.put("Areas", list);
		return nbt;
	}

	private static FarmAreas read(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		FarmAreas state = new FarmAreas();
		NbtList list = nbt.getList("Areas", NbtElement.COMPOUND_TYPE);
		for (int index = 0; index < list.size(); index++) {
			NbtCompound item = list.getCompound(index);
			int[] box = item.getIntArray("Box");
			if (box.length != 6 || !item.containsUuid("Player")) {
				continue;
			}
			Kind kind = kindOf(item);
			Built built = kind == Kind.FINISHED
					? new Built(item.getString("Farm"), item.getLong("FarmSignature"), FarmSite.read(item.getCompound("Site")), MaterialLedger.readSwaps(item))
					: null;
			Key key = new Key(item.getLong("Table"), item.getUuid("Player"), kind, item.getLong("Spot"));
			state.areas.put(key, new Area(new Box(box[0], box[1], box[2], box[3], box[4], box[5]), built));
		}
		return state;
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
