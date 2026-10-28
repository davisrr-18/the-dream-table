package com.davisr.thedreamtable.platform;

/**
 * The active loader's implementations of the seams, installed once by the loader's entrypoints before any
 * other mod code runs. The client seams are installed only on a client, so a dedicated server never loads them.
 */
public final class Platform {
	private static PlatformPaths paths;
	private static BlockBreakGuard blockBreaks;
	private static ServerNetwork network;
	private static ServerEvents serverEvents;
	private static Menus menus;
	private static Content content;
	private static ClientNetwork clientNetwork;
	private static ClientEvents clientEvents;
	private static ClientRendering clientRendering;

	private Platform() {
	}

	public static void install(PlatformPaths paths, BlockBreakGuard blockBreaks, ServerNetwork network,
			ServerEvents serverEvents, Menus menus, Content content) {
		Platform.paths = paths;
		Platform.blockBreaks = blockBreaks;
		Platform.network = network;
		Platform.serverEvents = serverEvents;
		Platform.menus = menus;
		Platform.content = content;
	}

	public static void installClient(ClientNetwork clientNetwork, ClientEvents clientEvents, ClientRendering clientRendering) {
		Platform.clientNetwork = clientNetwork;
		Platform.clientEvents = clientEvents;
		Platform.clientRendering = clientRendering;
	}

	public static PlatformPaths paths() {
		return installed(paths);
	}

	public static BlockBreakGuard blockBreaks() {
		return installed(blockBreaks);
	}

	public static ServerNetwork network() {
		return installed(network);
	}

	public static ServerEvents serverEvents() {
		return installed(serverEvents);
	}

	public static Menus menus() {
		return installed(menus);
	}

	public static Content content() {
		return installed(content);
	}

	public static ClientNetwork clientNetwork() {
		return installed(clientNetwork);
	}

	public static ClientEvents clientEvents() {
		return installed(clientEvents);
	}

	public static ClientRendering clientRendering() {
		return installed(clientRendering);
	}

	private static <T> T installed(T seam) {
		if (seam == null) {
			throw new IllegalStateException("The platform is not installed yet");
		}
		return seam;
	}
}
