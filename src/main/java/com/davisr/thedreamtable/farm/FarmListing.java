package com.davisr.thedreamtable.farm;

import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/**
 * Which of a player's finished farms My Farms shows, and in what order: a search by name, a filter by state and by
 * dimension, and a sort. Results are places in the list as the server sent it, newest first, since the farm buttons
 * name farms by that place.
 */
public final class FarmListing {
	private FarmListing() {
	}

	public enum Show {
		ALL, OWNED, SHARED, FAVORITES, KEPT_LOADED, ATTENTION, RUNNING, UNLOADED, FULL;

		boolean accepts(FarmLoader.Entry farm) {
			return switch (this) {
				case ALL -> true;
				case OWNED -> farm.role() == FarmRole.OWNER;
				case SHARED -> farm.role() != FarmRole.OWNER;
				case FAVORITES -> farm.favorite();
				case KEPT_LOADED -> farm.loaded();
				case ATTENTION -> needsAttention(farm);
				case RUNNING -> farm.available() && farm.status() == FarmStorage.RUNNING;
				case UNLOADED -> farm.available() && farm.status() == FarmStorage.UNLOADED;
				case FULL -> farm.status() == FarmStorage.FULL;
			};
		}
	}

	public enum Sort {
		NEWEST, NAME, STATUS, STORAGE, DISTANCE
	}

	/** Where the player stands, for sorting by distance. */
	public record Viewer(String dimension, BlockPos pos) {
	}

	/** Full storage, missing blocks, or a farm the mod no longer has: something the player should look at. */
	public static boolean needsAttention(FarmLoader.Entry farm) {
		return !farm.available() || farm.status() == FarmStorage.FULL || farm.missing() > 0;
	}

	/** The dimensions the player has farms in, in list order, for the dimension filter. */
	public static List<String> dimensions(List<FarmLoader.Entry> farms) {
		Set<String> found = new LinkedHashSet<>();
		for (FarmLoader.Entry farm : farms) {
			found.add(farm.dimension());
		}
		return new ArrayList<>(found);
	}

	/**
	 * The places of the farms to show, in order. An empty query or dimension matches every farm. Names are matched
	 * ignoring case; {@code name} gives each farm's shown name.
	 */
	public static List<Integer> shown(List<FarmLoader.Entry> farms, Function<FarmLoader.Entry, String> name, String query, Show show, String dimension,
			Sort sort, Viewer viewer) {
		String search = query.trim().toLowerCase(Locale.ROOT);
		List<Integer> shown = new ArrayList<>();
		for (int index = 0; index < farms.size(); index++) {
			FarmLoader.Entry farm = farms.get(index);
			if (show.accepts(farm) && (dimension.isEmpty() || dimension.equals(farm.dimension()))
					&& (search.isEmpty() || name.apply(farm).toLowerCase(Locale.ROOT).contains(search))) {
				shown.add(index);
			}
		}
		Comparator<Integer> newest = Comparator.naturalOrder();
		Comparator<Integer> byName = Comparator.comparing((Integer index) -> name.apply(farms.get(index)).toLowerCase(Locale.ROOT));
		Comparator<Integer> order = switch (sort) {
			case NEWEST -> newest;
			case NAME -> byName.thenComparing(newest);
			case STATUS -> Comparator.comparingInt((Integer index) -> statusRank(farms.get(index))).thenComparing(byName).thenComparing(newest);
			case STORAGE -> Comparator.comparingInt((Integer index) -> -farms.get(index).storage()).thenComparing(newest);
			case DISTANCE -> Comparator.comparingDouble((Integer index) -> distance(farms.get(index), viewer)).thenComparing(newest);
		};
		shown.sort(order);
		return shown;
	}

	/** Farms needing attention first, then running ones, then those not loaded. */
	private static int statusRank(FarmLoader.Entry farm) {
		if (needsAttention(farm)) {
			return 0;
		}
		return farm.status() == FarmStorage.RUNNING ? 1 : 2;
	}

	/** Squared distance in the player's dimension; farms in other dimensions come after every farm in this one. */
	private static double distance(FarmLoader.Entry farm, Viewer viewer) {
		if (viewer == null || !farm.dimension().equals(viewer.dimension())) {
			return Double.MAX_VALUE;
		}
		return farm.where().getSquaredDistance(viewer.pos());
	}

	/** Whole blocks from the player to the farm, or -1 when it is in another dimension. */
	public static int blocksAway(FarmLoader.Entry farm, Viewer viewer) {
		if (viewer == null || !farm.dimension().equals(viewer.dimension())) {
			return -1;
		}
		return (int) Math.sqrt(farm.where().getSquaredDistance(viewer.pos()));
	}

	public static int indexOfKey(List<FarmLoader.Entry> farms, String key) {
		for (int index = 0; index < farms.size(); index++) {
			if (farms.get(index).key().equals(key)) {
				return index;
			}
		}
		return -1;
	}
}
