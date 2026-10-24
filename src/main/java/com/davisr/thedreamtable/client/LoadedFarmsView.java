package com.davisr.thedreamtable.client;

import com.davisr.thedreamtable.farm.FarmLoader;
import com.davisr.thedreamtable.network.FarmDetailsPayload;
import com.davisr.thedreamtable.network.LoadedFarmsPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.util.Util;

import java.util.List;

/** The local player's finished farms as the server last sent them, for the My Farms pages. */
public final class LoadedFarmsView {
	private static final long DETAILS_EVERY_MS = 2000L;
	private static List<FarmLoader.Entry> farms = List.of();
	private static int limit;
	private static boolean received;
	private static boolean requested;
	private static String selected = "";
	private static FarmDetailsPayload details;
	private static long detailsAskedAt;

	private LoadedFarmsView() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(LoadedFarmsPayload.ID, (payload, context) -> {
			farms = payload.farms();
			limit = payload.limit();
			received = true;
		});
		ClientPlayNetworking.registerGlobalReceiver(FarmDetailsPayload.ID, (payload, context) -> {
			if (payload.key().equals(selected)) {
				details = payload;
			}
		});
	}

	/** The open farm's storage and size, asked for again every 2 seconds while its page is drawn; null until it arrives. */
	public static FarmDetailsPayload details() {
		long now = Util.getMeasuringTimeMs();
		if (now - detailsAskedAt >= DETAILS_EVERY_MS && !selected.isEmpty() && ClientPlayNetworking.canSend(FarmDetailsPayload.Request.ID)) {
			detailsAskedAt = now;
			ClientPlayNetworking.send(new FarmDetailsPayload.Request(selected));
		}
		return details != null && details.key().equals(selected) ? details : null;
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
		if (!key.equals(selected)) {
			details = null;
			detailsAskedAt = 0L;
		}
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
