package com.davisr.thedreamtable.client;

import com.davisr.thedreamtable.platform.Platform;
import net.minecraft.client.world.ClientWorld;

/**
 * Drops client state that belongs to one world: the table the hologram and clipboard follow and the other farms' areas
 * when the world or dimension changes, and everything the server sent when the player leaves it.
 */
public final class ClientLifecycle {
	private static ClientWorld world;

	private ClientLifecycle() {
	}

	public static void register() {
		Platform.clientEvents().onTickEnd(client -> {
			if (client.world != world) {
				world = client.world;
				leaveWorld();
			}
		});
		Platform.clientEvents().onDisconnect(client -> {
			leaveWorld();
			leaveServer();
		});
	}

	static void leaveWorld() {
		DreamTableHologram.clear();
		DreamTableClipboard.close();
		OtherFarms.clear();
	}

	static void leaveServer() {
		LoadedFarmsView.reset();
		TrackedFarmHud.clear();
	}
}
