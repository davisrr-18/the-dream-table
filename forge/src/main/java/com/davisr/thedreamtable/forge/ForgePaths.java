package com.davisr.thedreamtable.forge;

import com.davisr.thedreamtable.platform.PlatformPaths;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;

/** Forge's folders. */
final class ForgePaths implements PlatformPaths {
	@Override
	public Path configDir() {
		return FMLPaths.CONFIGDIR.get();
	}

	@Override
	public Path gameDir() {
		return FMLPaths.GAMEDIR.get();
	}
}
