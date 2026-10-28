package com.davisr.thedreamtable.neoforge;

import com.davisr.thedreamtable.platform.PlatformPaths;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;

/** NeoForge's folders. */
final class NeoForgePaths implements PlatformPaths {
	@Override
	public Path configDir() {
		return FMLPaths.CONFIGDIR.get();
	}

	@Override
	public Path gameDir() {
		return FMLPaths.GAMEDIR.get();
	}
}
