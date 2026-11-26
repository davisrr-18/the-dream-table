package com.davisr.thedreamtable.block.entity;

import com.davisr.thedreamtable.farm.FarmOptions;
import com.davisr.thedreamtable.farm.FarmSite;
import com.davisr.thedreamtable.farm.FarmTemplate;
import com.davisr.thedreamtable.farm.FarmTemplates;
import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * The farms a player lined up at a table, built one after another. An entry is only a plan: {@code SelectedFarm}, the spot
 * aimed at ({@link AimedSpot#toNbt}) and the material {@code Swaps}, with the same keys as a saved session. It holds no items
 * and no progress, so dropping or losing one can never lose a delivered item; the materials of a queued farm are delivered
 * when its turn comes. Queued spots reserve nothing: they are checked again when their turn comes.
 */
public final class SessionQueue {
	/** Farms one player can line up at one table. */
	static final int MAX = 8;
	private static final String KEY = "Queue";

	private final List<NbtCompound> entries = new ArrayList<>();
	/** The footprints worked out for the entries, by entry; see {@link #areas}. */
	private final Map<NbtCompound, Resolved> resolved = new IdentityHashMap<>();
	/** The areas of the copy this queue was read from; only the owner's client has any. */
	private final List<Area> received = new ArrayList<>();

	boolean isEmpty() {
		return entries.isEmpty();
	}

	int size() {
		return entries.size();
	}

	boolean full() {
		return entries.size() >= MAX;
	}

	void clear() {
		entries.clear();
		resolved.clear();
	}

	void add(NbtCompound entry) {
		entries.add(entry);
	}

	/** Takes the first entry off the line, or null when it is empty. */
	@Nullable NbtCompound pop() {
		if (entries.isEmpty()) {
			return null;
		}
		NbtCompound head = entries.remove(0);
		resolved.remove(head);
		return head;
	}

	/** The farm ids in line order; an entry that names none shows as an empty id. */
	List<String> farms() {
		return entries.stream().map(entry -> entry.getString("SelectedFarm")).toList();
	}

	/** The farm's title, or its id for an imported schematic. */
	public static Text name(String farm) {
		return FarmOptions.byId(farm).map(FarmOptions.FarmOption::title).orElse(Text.literal(farm));
	}

	static NbtCompound entry(String farm, AimedSpot spot, Map<Item, Item> swaps) {
		NbtCompound entry = new NbtCompound();
		entry.putString("SelectedFarm", farm);
		entry.put("Aim", spot.toNbt());
		MaterialLedger.writeSwaps(entry, swaps);
		return entry;
	}

	/**
	 * Whether the next queued farm starts by itself: the active build is finished and recorded, there is one to start and its
	 * owner is online, since their permissions and game mode decide every step. A finished repair never starts the queue: it is
	 * not a farm of the queue's line. Never asked from inside the build's finish.
	 */
	static boolean shouldStartNext(boolean done, boolean finishedRecorded, boolean queueEmpty, boolean ownerOnline, boolean repair) {
		return done && finishedRecorded && !queueEmpty && ownerOnline && !repair;
	}

	/** Puts the other queue's entries behind this one's, up to {@link #MAX}: a restored project keeps its own line first. */
	void append(SessionQueue other) {
		for (NbtCompound entry : other.entries) {
			if (full()) {
				return;
			}
			entries.add(entry.copy());
		}
	}

	/** Whether the area touches any queued farm standing in this world; a queued farm whose file is gone or whose spot is unreadable is skipped. */
	boolean overlaps(Identifier world, Box area) {
		for (Area queued : areas()) {
			if (queued != null && queued.dimension().equals(world) && queued.box().intersects(area)) {
				return true;
			}
		}
		return false;
	}

	/** The box a queued farm would occupy in this world, or null when it is for another world, its spot is unreadable or its farm is unknown. */
	static @Nullable Box footprint(NbtCompound entry, Identifier world) {
		Area area = area(entry, FarmTemplates.get(entry.getString("SelectedFarm")));
		return area != null && area.dimension().equals(world) ? area.box() : null;
	}

	/** Where a queued farm would stand and in which world: what the owner's client outlines. */
	public record Area(Identifier dimension, Box box) {
	}

	private record Resolved(@Nullable FarmTemplate template, @Nullable Area area) {
	}

	private static @Nullable Area area(NbtCompound entry, @Nullable FarmTemplate template) {
		AimedSpot spot = entry.contains("Aim", NbtElement.COMPOUND_TYPE) ? AimedSpot.fromNbt(entry.getCompound("Aim")) : null;
		if (spot == null || template == null || template.steps().isEmpty()) {
			return null;
		}
		// The anchor is what places the farm, so the table position is never read.
		return new Area(spot.dimension(), new FarmSite(spot.anchor(), spot.anchor(), spot.facing(), spot.mirrored()).footprint(template));
	}

	/**
	 * The area of each entry in line order, null for one whose farm or spot can't be resolved. A footprint walks every step of the
	 * farm, and the owner's copy is built on each change of the session, so it is kept per entry and recomputed only when the
	 * farm's template was replaced.
	 */
	private List<Area> areas() {
		List<Area> areas = new ArrayList<>();
		Map<NbtCompound, Resolved> kept = new IdentityHashMap<>();
		for (NbtCompound entry : entries) {
			FarmTemplate template = FarmTemplates.get(entry.getString("SelectedFarm"));
			Resolved known = resolved.get(entry);
			if (known == null || known.template() != template) {
				known = new Resolved(template, area(entry, template));
			}
			kept.put(entry, known);
			areas.add(known.area());
		}
		resolved.clear();
		resolved.putAll(kept);
		return areas;
	}

	/** On the client: the areas the server sent with this copy, empty on the server. */
	public List<Area> clientAreas() {
		return List.copyOf(received);
	}

	/** Written only when not empty, so a session without a queue saves exactly as before. */
	void write(NbtCompound nbt) {
		if (entries.isEmpty()) {
			return;
		}
		NbtList list = new NbtList();
		entries.forEach(entry -> list.add(entry.copy()));
		nbt.put(KEY, list);
	}

	/**
	 * The copy for the owner's client: each farm's name, which its Work page shows, and where it would stand ({@code Dimension} and
	 * {@code Box}, six ints from the low corner to the far corner), which the world outlines. An entry that can't be resolved sends no box.
	 */
	void writeForClient(NbtCompound nbt) {
		if (entries.isEmpty()) {
			return;
		}
		NbtList list = new NbtList();
		List<Area> areas = areas();
		for (int index = 0; index < entries.size(); index++) {
			NbtCompound entry = new NbtCompound();
			entry.putString("SelectedFarm", entries.get(index).getString("SelectedFarm"));
			Area area = areas.get(index);
			if (area != null) {
				Box box = area.box();
				entry.putString("Dimension", area.dimension().toString());
				entry.putIntArray("Box", new int[] {(int) box.minX, (int) box.minY, (int) box.minZ, (int) box.maxX, (int) box.maxY, (int) box.maxZ});
			}
			list.add(entry);
		}
		nbt.put(KEY, list);
	}

	/**
	 * Keeps the first {@link #MAX} compounds of a saved queue, each cut down to the keys an entry has, so a damaged or
	 * edited save can't grow a session without bound. A missing or non-compound list reads as empty.
	 */
	void read(NbtCompound nbt) {
		entries.clear();
		resolved.clear();
		received.clear();
		NbtList list = nbt.getList(KEY, NbtElement.COMPOUND_TYPE);
		for (int index = 0; index < list.size() && entries.size() < MAX; index++) {
			NbtCompound saved = list.getCompound(index);
			Identifier dimension = Identifier.tryParse(saved.getString("Dimension"));
			if (dimension != null && saved.contains("Box", NbtElement.INT_ARRAY_TYPE) && saved.getIntArray("Box").length == 6) {
				int[] box = saved.getIntArray("Box");
				received.add(new Area(dimension, new Box(box[0], box[1], box[2], box[3], box[4], box[5])));
			}
			NbtCompound entry = new NbtCompound();
			entry.putString("SelectedFarm", saved.getString("SelectedFarm"));
			if (saved.contains("Aim", NbtElement.COMPOUND_TYPE)) {
				entry.put("Aim", saved.getCompound("Aim").copy());
			}
			if (saved.contains("Swaps", NbtElement.LIST_TYPE)) {
				entry.put("Swaps", saved.getList("Swaps", NbtElement.COMPOUND_TYPE).copy());
			}
			entries.add(entry);
		}
	}
}
