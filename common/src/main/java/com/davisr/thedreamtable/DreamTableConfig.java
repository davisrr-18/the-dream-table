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
	private static final String FARM_SHARING = "farm_sharing_enabled";
	private static final String MAX_COLLABORATORS = "max_collaborators_per_farm";
	private static final String MAX_PENDING_INVITES = "max_pending_invites_per_player";
	private static final String DELIVER_NEARBY = "deliver_from_nearby_enabled";
	private static final String FARM_PROTECTION = "farm_protection_enabled";
	public static final int MAX_LOADED_DEFAULT = 3;
	public static final int MAX_LOADED_LIMIT = 16;
	public static final int MAX_COLLABORATORS_DEFAULT = 8;
	public static final int MAX_COLLABORATORS_LIMIT = 64;
	public static final int MAX_PENDING_INVITES_DEFAULT = 32;
	public static final int MAX_PENDING_INVITES_LIMIT = 256;

	private static volatile int maxBlocksPerSecond = BuildPace.MAX_RATE;
	private static volatile int maxLoadedFarms = MAX_LOADED_DEFAULT;
	private static volatile boolean loadWhileOffline;
	private static volatile boolean farmSharingEnabled = true;
	private static volatile int maxCollaboratorsPerFarm = MAX_COLLABORATORS_DEFAULT;
	private static volatile int maxPendingInvitesPerPlayer = MAX_PENDING_INVITES_DEFAULT;
	private static volatile boolean deliverFromNearby = true;
	private static volatile boolean farmProtection = true;

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

	public static boolean farmSharingEnabled() {
		return farmSharingEnabled;
	}

	public static int maxCollaboratorsPerFarm() {
		return maxCollaboratorsPerFarm;
	}

	public static int maxPendingInvitesPerPlayer() {
		return maxPendingInvitesPerPlayer;
	}

	/** Whether "Deliver from Nearby" may take materials from containers around the table; off turns it off for every player. */
	public static boolean deliverFromNearby() {
		return deliverFromNearby;
	}

	/** Whether players may protect their finished farms from other players; off turns "Protect My Farms" off for everyone. */
	public static boolean farmProtectionEnabled() {
		return farmProtection;
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
		loadWhileOffline = Boolean.parseBoolean(properties.getProperty(LOAD_OFFLINE, "false").trim());
		farmSharingEnabled = Boolean.parseBoolean(properties.getProperty(FARM_SHARING, "true").trim());
		maxCollaboratorsPerFarm = clamped(properties, MAX_COLLABORATORS, MAX_COLLABORATORS_DEFAULT, MAX_COLLABORATORS_LIMIT);
		maxPendingInvitesPerPlayer = clamped(properties, MAX_PENDING_INVITES, MAX_PENDING_INVITES_DEFAULT, MAX_PENDING_INVITES_LIMIT);
		deliverFromNearby = Boolean.parseBoolean(properties.getProperty(DELIVER_NEARBY, "true").trim());
		farmProtection = Boolean.parseBoolean(properties.getProperty(FARM_PROTECTION, "true").trim());
		properties.setProperty(MAX_RATE, Integer.toString(maxBlocksPerSecond));
		properties.setProperty(MAX_LOADED, Integer.toString(maxLoadedFarms));
		properties.setProperty(LOAD_OFFLINE, Boolean.toString(loadWhileOffline));
		properties.setProperty(FARM_SHARING, Boolean.toString(farmSharingEnabled));
		properties.setProperty(MAX_COLLABORATORS, Integer.toString(maxCollaboratorsPerFarm));
		properties.setProperty(MAX_PENDING_INVITES, Integer.toString(maxPendingInvitesPerPlayer));
		properties.setProperty(DELIVER_NEARBY, Boolean.toString(deliverFromNearby));
		properties.setProperty(FARM_PROTECTION, Boolean.toString(farmProtection));
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path)) {
				properties.store(writer, "The Dream Table. " + MAX_RATE + ": fastest a build may go, 1 to " + BuildPace.MAX_LIMIT
						+ " (default " + BuildPace.MAX_RATE + "). " + MAX_LOADED + ": finished farms each player may keep loaded, 0 to "
						+ MAX_LOADED_LIMIT + " (default " + MAX_LOADED_DEFAULT + "). " + LOAD_OFFLINE + ": keep them loaded while the player is offline. "
						+ FARM_SHARING + ": allow accepted farm collaborators. " + MAX_COLLABORATORS + ": 0 to " + MAX_COLLABORATORS_LIMIT
						+ " (default " + MAX_COLLABORATORS_DEFAULT + "). " + MAX_PENDING_INVITES + ": 0 to " + MAX_PENDING_INVITES_LIMIT
						+ " (default " + MAX_PENDING_INVITES_DEFAULT + "). " + DELIVER_NEARBY + ": let players deliver materials from chests, barrels and shulker boxes within 6 blocks of the table (only vanilla locks and claim mods' break checks keep a container out). "
						+ FARM_PROTECTION + ": let players stop others from breaking the structure of their finished farms.");
			}
		} catch (IOException exception) {
			TheDreamTableMod.LOGGER.warn("Could not write {}", path, exception);
		}
	}

	private static int parse(String value, int fallback) {
		try {
			return value == null ? fallback : Integer.parseInt(value.trim());
		} catch (NumberFormatException exception) {
			return fallback;
		}
	}

	private static int clamped(Properties properties, String key, int fallback, int limit) {
		return Math.max(0, Math.min(limit, parse(properties.getProperty(key), fallback)));
	}
}
