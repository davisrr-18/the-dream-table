package com.davisr.thedreamtable.client;

import com.davisr.thedreamtable.farm.FarmLoader;
import com.davisr.thedreamtable.network.LoadedFarmsPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.List;

/** The local player's finished farms as the server last sent them, for the Keep Farms Loaded page. */
public final class LoadedFarmsView {
	private static List<FarmLoader.Entry> farms = List.of();
	private static int limit;
	private static boolean received;
	private static boolean requested;

	private LoadedFarmsView() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(LoadedFarmsPayload.ID, (payload, context) -> {
			farms = payload.farms();
			limit = payload.limit();
			received = true;
		});
	}

	public static List<FarmLoader.Entry> farms() {
		return farms;
	}

	public static int limit() {
		return limit;
	}

	public static boolean received() {
		return received;
	}

	public static long loaded() {
		return farms.stream().filter(FarmLoader.Entry::loaded).count();
	}

	/** Asks once per visit to the page, since farms can be finished or taken down while it is closed. */
	public static void ensure() {
		if (!requested) {
			requested = true;
			send("");
		}
	}

	public static void invalidate() {
		requested = false;
		received = false;
	}

	public static void toggle(String key) {
		send(key);
	}

	private static void send(String toggle) {
		if (ClientPlayNetworking.canSend(LoadedFarmsPayload.Request.ID)) {
			ClientPlayNetworking.send(new LoadedFarmsPayload.Request(toggle));
		}
	}
}
