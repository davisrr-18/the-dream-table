package com.davisr.thedreamtable.farm;

import com.davisr.thedreamtable.block.entity.DreamTableBlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.PersistentState;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The areas of every farm being built or taken apart in a world, across all tables. A new farm can't overlap any of them,
 * and a build never clears blocks inside another one. Entries whose table is gone are dropped when they are next looked at.
 */
public final class FarmAreas extends PersistentState {
	private static final Type<FarmAreas> TYPE = new Type<>(FarmAreas::new, FarmAreas::read, null);

	private final Map<Key, Box> areas = new LinkedHashMap<>();

	/** One player's farm at one table. A farm being taken apart is kept apart from that player's next farm. */
	public record Key(long table, UUID player, boolean dismantling) {
		public static Key build(BlockPos table, UUID player) {
			return new Key(table.asLong(), player, false);
		}

		public static Key dismantle(BlockPos table, UUID player) {
			return new Key(table.asLong(), player, true);
		}
	}

	public static FarmAreas of(ServerWorld world) {
		return world.getPersistentStateManager().getOrCreate(TYPE, "the_dream_table_farm_areas");
	}

	public void claim(Key key, Box area) {
		if (!area.equals(areas.put(key, area))) {
			markDirty();
		}
	}

	public void release(Key key) {
		if (areas.remove(key) != null) {
			markDirty();
		}
	}

	public void releaseTable(BlockPos table) {
		if (areas.keySet().removeIf(key -> key.table() == table.asLong())) {
			markDirty();
		}
	}

	/** True when the area overlaps a farm other than the asker's own. */
	public boolean overlaps(ServerWorld world, Key asker, Box area) {
		return find(world, asker, other -> other.intersects(area));
	}

	/** True when the spot lies inside a farm other than the asker's own. */
	public boolean reservedByOther(ServerWorld world, Key asker, BlockPos pos) {
		return find(world, asker, other -> other.contains(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
	}

	private boolean find(ServerWorld world, Key asker, Predicate<Box> test) {
		Iterator<Map.Entry<Key, Box>> entries = areas.entrySet().iterator();
		while (entries.hasNext()) {
			Map.Entry<Key, Box> entry = entries.next();
			if (entry.getKey().equals(asker)) {
				continue;
			}
			if (tableGone(world, entry.getKey())) {
				entries.remove();
				markDirty();
				continue;
			}
			if (test.test(entry.getValue())) {
				return true;
			}
		}
		return false;
	}

	/** Only a loaded chunk can prove the table is gone; an unloaded table keeps its area. */
	private static boolean tableGone(ServerWorld world, Key key) {
		BlockPos table = BlockPos.fromLong(key.table());
		return world.isChunkLoaded(table) && !(world.getBlockEntity(table) instanceof DreamTableBlockEntity);
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		NbtList list = new NbtList();
		for (Map.Entry<Key, Box> entry : areas.entrySet()) {
			NbtCompound item = new NbtCompound();
			item.putLong("Table", entry.getKey().table());
			item.putUuid("Player", entry.getKey().player());
			item.putBoolean("Dismantling", entry.getKey().dismantling());
			Box box = entry.getValue();
			item.putIntArray("Box", new int[]{(int) box.minX, (int) box.minY, (int) box.minZ, (int) box.maxX, (int) box.maxY, (int) box.maxZ});
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
			if (box.length == 6 && item.containsUuid("Player")) {
				Key key = new Key(item.getLong("Table"), item.getUuid("Player"), item.getBoolean("Dismantling"));
				state.areas.put(key, new Box(box[0], box[1], box[2], box[3], box[4], box[5]));
			}
		}
		return state;
	}
}
