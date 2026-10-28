package com.davisr.thedreamtable.client;

import com.davisr.thedreamtable.TheDreamTableMod;
import com.davisr.thedreamtable.platform.Platform;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** This player's own preferences, in {@code config/the_dream_table-client.properties}. They follow the player to every world and server. */
public final class ClientSettings {
	static final String FILE = "the_dream_table-client.properties";
	private static final String SHOW_OTHER_FARMS = "show_other_farms";
	private static final String SHOW_TRACKED_FARM = "show_tracked_farm";
	private static final String DELIVER_NEARBY = "deliver_from_nearby";

	private static boolean showOtherFarms;
	private static boolean showTrackedFarm = true;
	private static boolean deliverFromNearby = true;

	private ClientSettings() {
	}

	public static boolean showOtherFarms() {
		return showOtherFarms;
	}

	public static void setShowOtherFarms(boolean shown) {
		showOtherFarms = shown;
		save();
	}

	public static boolean showTrackedFarm() {
		return showTrackedFarm;
	}

	public static void setShowTrackedFarm(boolean shown) {
		showTrackedFarm = shown;
		save();
	}

	/** Whether this player wants the "Deliver from Nearby" button; the server's own setting still decides if it works. */
	public static boolean deliverFromNearby() {
		return deliverFromNearby;
	}

	public static void setDeliverFromNearby(boolean enabled) {
		deliverFromNearby = enabled;
		save();
	}

	public static void load() {
		Path path = path();
		if (!Files.isRegularFile(path)) {
			return;
		}
		Properties properties = new Properties();
		try (Reader reader = Files.newBufferedReader(path)) {
			properties.load(reader);
			showOtherFarms = Boolean.parseBoolean(properties.getProperty(SHOW_OTHER_FARMS, "false"));
			showTrackedFarm = Boolean.parseBoolean(properties.getProperty(SHOW_TRACKED_FARM, "true"));
			deliverFromNearby = Boolean.parseBoolean(properties.getProperty(DELIVER_NEARBY, "true"));
		} catch (IOException exception) {
			TheDreamTableMod.LOGGER.warn("Could not read {}", path, exception);
		}
	}

	private static void save() {
		Path path = path();
		Properties properties = new Properties();
		properties.setProperty(SHOW_OTHER_FARMS, Boolean.toString(showOtherFarms));
		properties.setProperty(SHOW_TRACKED_FARM, Boolean.toString(showTrackedFarm));
		properties.setProperty(DELIVER_NEARBY, Boolean.toString(deliverFromNearby));
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path)) {
				properties.store(writer, "The Dream Table client settings");
			}
		} catch (IOException exception) {
			TheDreamTableMod.LOGGER.warn("Could not write {}", path, exception);
		}
	}

	private static Path path() {
		return Platform.paths().configDir().resolve(FILE);
	}
}
