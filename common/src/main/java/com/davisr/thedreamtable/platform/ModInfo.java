package com.davisr.thedreamtable.platform;

/** The mod's version and loader, read from the running loader's own metadata. */
public interface ModInfo {
	/** "fabric", "neoforge" or "forge". */
	String loader();

	/** The mod's version as its metadata states it, or "unknown". */
	String version();
}
