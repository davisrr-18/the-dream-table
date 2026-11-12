package com.davisr.thedreamtable.network;

/**
 * The mod's network protocol version, shared by every loader. Bump it whenever a payload's id or codec changes, so a
 * client and a server that cannot understand each other refuse to connect with a clear message instead of failing on a
 * decode error later. NeoForge registers its payloads with it and Forge uses it, as a number, as its channel's version
 * (both loaders refuse a mismatch themselves); Fabric exchanges it in a configuration-phase handshake
 * ({@code FabricHandshake}).
 */
public final class NetworkProtocol {
	public static final String VERSION = "4";

	private NetworkProtocol() {
	}

	/** Whether a peer that reports {@code remote} can talk to this install. */
	public static boolean compatible(String remote) {
		return VERSION.equals(remote);
	}
}
