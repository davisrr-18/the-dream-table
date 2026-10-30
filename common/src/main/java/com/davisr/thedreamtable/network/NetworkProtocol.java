package com.davisr.thedreamtable.network;

/**
 * The mod's network protocol version, shared by both loaders. Bump it whenever a payload's id or codec changes, so a
 * client and a server that cannot understand each other refuse to connect with a clear message instead of failing on a
 * decode error later. NeoForge registers its payloads with it (the loader refuses a mismatch itself); Fabric exchanges
 * it in a configuration-phase handshake ({@code FabricHandshake}).
 */
public final class NetworkProtocol {
	public static final String VERSION = "3";

	private NetworkProtocol() {
	}

	/** Whether a peer that reports {@code remote} can talk to this install. */
	public static boolean compatible(String remote) {
		return VERSION.equals(remote);
	}
}
