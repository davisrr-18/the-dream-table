package com.davisr.thedreamtable.platform;

import java.nio.file.Path;

/** Folders the loader owns: where settings files go and where players keep their game files. */
public interface PlatformPaths {
	Path configDir();

	Path gameDir();
}
