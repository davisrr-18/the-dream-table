package com.davisr.thedreamtable;

import com.davisr.thedreamtable.farm.BuildPace;
import com.davisr.thedreamtable.platform.Platform;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Server settings in {@code config/the_dream_table.properties}, written with defaults the first time.
 * Read when the server starts, so a server owner can change them between restarts.
 */
public final class DreamTableConfig {
	static final String FILE = "the_dream_table.properties";
	private static final String MAX_RATE = "max_blocks_per_second";
	private static final String MAX_LOADED = "max_loaded_farms_per_player";
	private static final String LOAD_OFFLINE = "load_farms_while_offline";
	private static final String DELIVER_NEARBY = "deliver_from_nearby_enabled";
	private static final String FARM_PROTECTION = "farm_protection_enabled";
	private static final String TABLE_BREAK_GUARD = "table_break_guard_enabled";
	private static final String SHOW_OTHER_FARMS = "show_other_farms_enabled";
	public static final int MAX_LOADED_DEFAULT = 3;
	public static final int MAX_LOADED_LIMIT = 16;

	private static volatile int maxBlocksPerSecond = BuildPace.MAX_RATE;
	private static volatile int maxLoadedFarms = MAX_LOADED_DEFAULT;
	private static volatile boolean loadWhileOffline;
	private static volatile boolean deliverFromNearby = true;
	private static volatile boolean farmProtection = true;
	private static volatile boolean tableBreakGuard = true;
	private static volatile boolean showOtherFarms = true;

	private DreamTableConfig() {
	}

	/** The fastest any build or Creative build may go, in blocks per second. */
	public static int maxBlocksPerSecond() {
		return maxBlocksPerSecond;
	}

	/** How many finished farms each player may keep loaded; 0 turns the option off for everyone. */
	public static int maxLoadedFarms() {
		return maxLoadedFarms;
	}

	public static boolean loadWhileOffline() {
		return loadWhileOffline;
	}

	/** Whether "Deliver from Nearby" may take materials from containers around the table; off turns it off for every player. */
	public static boolean deliverFromNearby() {
		return deliverFromNearby;
	}

	/** Whether players may protect their finished farms from other players; off turns "Protect My Farms" off for everyone. */
	/** Whether a Dream Table holding another player's materials or build is kept from being broken by anyone else. */
	public static boolean tableBreakGuardEnabled() {
		return tableBreakGuard;
	}

	public static boolean farmProtectionEnabled() {
		return farmProtection;
	}

	/** Whether players may see where other players' farms are ("Show Other Farms"); off means the server sends no areas at all. */
	public static boolean showOtherFarmsEnabled() {
		return showOtherFarms;
	}

	public static void load() {
		Path path = Platform.paths().configDir().resolve(FILE);
		Properties properties = new Properties();
		if (Files.isRegularFile(path)) {
			try (Reader reader = Files.newBufferedReader(path)) {
				properties.load(reader);
			} catch (IOException exception) {
				TheDreamTableMod.LOGGER.warn("Could not read {}, using defaults", path, exception);
			}
		}
		maxBlocksPerSecond = BuildPace.clampMax(parse(properties.getProperty(MAX_RATE), BuildPace.MAX_RATE));
		maxLoadedFarms = Math.max(0, Math.min(MAX_LOADED_LIMIT, parse(properties.getProperty(MAX_LOADED), MAX_LOADED_DEFAULT)));
		loadWhileOffline = flag(properties, LOAD_OFFLINE, false);
		deliverFromNearby = flag(properties, DELIVER_NEARBY, true);
		farmProtection = flag(properties, FARM_PROTECTION, true);
		tableBreakGuard = flag(properties, TABLE_BREAK_GUARD, true);
		showOtherFarms = flag(properties, SHOW_OTHER_FARMS, true);
		properties.setProperty(MAX_RATE, Integer.toString(maxBlocksPerSecond));
		properties.setProperty(MAX_LOADED, Integer.toString(maxLoadedFarms));
		properties.setProperty(LOAD_OFFLINE, Boolean.toString(loadWhileOffline));
		properties.setProperty(DELIVER_NEARBY, Boolean.toString(deliverFromNearby));
		properties.setProperty(FARM_PROTECTION, Boolean.toString(farmProtection));
		properties.setProperty(TABLE_BREAK_GUARD, Boolean.toString(tableBreakGuard));
		properties.setProperty(SHOW_OTHER_FARMS, Boolean.toString(showOtherFarms));
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path)) {
				properties.store(writer, "The Dream Table. " + MAX_RATE + ": fastest a build may go, 1 to " + BuildPace.MAX_LIMIT
						+ " (default " + BuildPace.MAX_RATE + "). " + MAX_LOADED + ": finished farms each player may keep loaded, 0 to "
						+ MAX_LOADED_LIMIT + " (default " + MAX_LOADED_DEFAULT + "). " + LOAD_OFFLINE + ": keep them loaded while the player is offline. "
						+ DELIVER_NEARBY + ": let players deliver materials from chests, barrels and shulker boxes within 6 blocks of the table (only vanilla locks and claim mods' break checks keep a container out). "
						+ FARM_PROTECTION + ": let players stop others from breaking the structure of their finished farms. "
						+ TABLE_BREAK_GUARD + ": keep a table that holds another player's delivered materials or build from being broken by anyone but that player or an operator; false lets anyone break it (an escape for tables of players who never return). "
						+ SHOW_OTHER_FARMS + ": let players outline other players' farm areas near them; false stops the server from sending them (privacy).");
			}
		} catch (IOException exception) {
			TheDreamTableMod.LOGGER.warn("Could not write {}", path, exception);
		}
	}

	private static boolean flag(Properties properties, String key, boolean fallback) {
		return flag(key, properties.getProperty(key), fallback);
	}

	/**
	 * {@code true} or {@code false}, in any case. A missing value is the default without a word; anything else, like
	 * {@code yes}, is logged and also becomes the default, instead of silently switching the option off.
	 */
	static boolean flag(String key, String value, boolean fallback) {
		if (value == null) {
			return fallback;
		}
		String trimmed = value.trim();
		if (trimmed.equalsIgnoreCase("true")) {
			return true;
		}
		if (trimmed.equalsIgnoreCase("false")) {
			return false;
		}
		TheDreamTableMod.LOGGER.warn("{} in {} must be true or false, not \"{}\"; using the default, {}", key, FILE, trimmed, fallback);
		return fallback;
	}

	private static int parse(String value, int fallback) {
		try {
			return value == null ? fallback : Integer.parseInt(value.trim());
		} catch (NumberFormatException exception) {
			return fallback;
		}
	}
}
