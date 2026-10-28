package com.davisr.thedreamtable.fabric;

import com.davisr.thedreamtable.platform.Platform;

/** Installs the Fabric implementations of the platform seams. */
public final class FabricPlatform {
	private FabricPlatform() {
	}

	public static void install() {
		Platform.install(new FabricPaths(), new FabricBlockBreakGuard(), new FabricServerNetwork(),
				new FabricServerEvents(), new FabricMenus(), new FabricContent());
	}

	public static void installClient() {
		Platform.installClient(new FabricClientNetwork(), new FabricClientEvents(), new FabricClientRendering());
	}
}
