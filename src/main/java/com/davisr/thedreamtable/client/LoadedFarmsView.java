package com.davisr.thedreamtable.client;

import com.davisr.thedreamtable.farm.FarmLoader;
import com.davisr.thedreamtable.network.LoadedFarmsPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.List;

/** The local player's finished farms as the server last sent them, for the My Farms pages. */
public final class LoadedFarmsView {
	private static List<FarmLoader.Entry> farms = List.of();
	private static int limit;
	private static boolean received;
	private static boolean requested;
	private static String selected = "";

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

	/** The farm whose page is open, by key, so it stays the same farm when the list is refreshed. */
	public static void select(String key) {
		selected = key;
	}

	public static int selectedIndex() {
		for (int index = 0; index < farms.size(); index++) {
			if (farms.get(index).key().equals(selected)) {
				return index;
			}
		}
		return -1;
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
