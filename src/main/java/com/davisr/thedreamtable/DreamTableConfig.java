package com.davisr.thedreamtable;

import com.davisr.thedreamtable.farm.BuildPace;
import net.fabricmc.loader.api.FabricLoader;

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
	private static final String FILE = "the_dream_table.properties";
	private static final String MAX_RATE = "max_blocks_per_second";

	private static volatile int maxBlocksPerSecond = BuildPace.MAX_RATE;

	private DreamTableConfig() {
	}

	/** The fastest any build or Creative build may go, in blocks per second. */
	public static int maxBlocksPerSecond() {
		return maxBlocksPerSecond;
	}

	public static void load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE);
		Properties properties = new Properties();
		if (Files.isRegularFile(path)) {
			try (Reader reader = Files.newBufferedReader(path)) {
				properties.load(reader);
			} catch (IOException exception) {
				TheDreamTableMod.LOGGER.warn("Could not read {}, using defaults", path, exception);
			}
		}
		maxBlocksPerSecond = BuildPace.clampMax(parse(properties.getProperty(MAX_RATE), BuildPace.MAX_RATE));
		properties.setProperty(MAX_RATE, Integer.toString(maxBlocksPerSecond));
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path)) {
				properties.store(writer, "The Dream Table. " + MAX_RATE + ": fastest a build may go, 1 to " + BuildPace.MAX_LIMIT + " (default " + BuildPace.MAX_RATE + ")");
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
}
