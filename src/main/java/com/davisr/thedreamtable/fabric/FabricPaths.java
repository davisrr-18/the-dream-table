package com.davisr.thedreamtable.fabric;

import com.davisr.thedreamtable.platform.PlatformPaths;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

/** Fabric Loader's folders. */
final class FabricPaths implements PlatformPaths {
	@Override
	public Path configDir() {
		return FabricLoader.getInstance().getConfigDir();
	}

	@Override
	public Path gameDir() {
		return FabricLoader.getInstance().getGameDir();
	}
}
