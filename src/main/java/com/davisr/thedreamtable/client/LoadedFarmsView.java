package com.davisr.thedreamtable.client;

import com.davisr.thedreamtable.farm.FarmListing;
import com.davisr.thedreamtable.farm.FarmLoader;
import com.davisr.thedreamtable.network.FarmCarePayload;
import com.davisr.thedreamtable.network.FarmActionResultPayload;
import com.davisr.thedreamtable.network.FarmCollaboratorPayload;
import com.davisr.thedreamtable.network.FarmDetailsPayload;
import com.davisr.thedreamtable.network.FarmInvitePayload;
import com.davisr.thedreamtable.network.FarmInviteResponsePayload;
import com.davisr.thedreamtable.network.FarmPreferencePayload;
import com.davisr.thedreamtable.network.FarmRenamePayload;
import com.davisr.thedreamtable.network.LoadedFarmsPayload;
import com.davisr.thedreamtable.farm.FarmRole;
import com.davisr.thedreamtable.screen.DreamTableScreenHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * The local player's finished farms as the server last sent them, for the My Farms pages, and how the list is
 * filtered and sorted, which stays while the menu is open.
 */
public final class LoadedFarmsView {
	private static final long DETAILS_EVERY_MS = 2000L;
	private static List<FarmLoader.Entry> farms = List.of();
	private static List<LoadedFarmsPayload.InviteEntry> invites = List.of();
	private static int limit;
	private static boolean received;
	private static boolean requested;
	private static String selected = "";
	private static FarmDetailsPayload details;
	private static long detailsAskedAt;
	private static FarmListing.Show show = FarmListing.Show.ALL;
	private static FarmListing.Sort sort = FarmListing.Sort.NEWEST;
	private static String dimension = "";

	private LoadedFarmsView() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(LoadedFarmsPayload.ID, (payload, context) -> {
			farms = payload.farms();
			invites = payload.invites();
			limit = payload.limit();
			received = true;
		});
		ClientPlayNetworking.registerGlobalReceiver(FarmDetailsPayload.ID, (payload, context) -> {
			if (payload.key().equals(selected)) {
				details = payload;
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(FarmActionResultPayload.ID, (payload, context) -> {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client.player != null) {
				client.inGameHud.setOverlayMessage(Text.translatable("gui.the_dream_table.action_result."
						+ payload.result().name().toLowerCase()), false);
			}
		});
	}

	/** The open farm's storage and size, asked for again every 2 seconds while its page is drawn; null until it arrives. */
	public static FarmDetailsPayload details() {
		long now = Util.getMeasuringTimeMs();
		if (now - detailsAskedAt >= DETAILS_EVERY_MS && !selected.isEmpty() && ClientPlayNetworking.canSend(FarmDetailsPayload.Request.ID)) {
			detailsAskedAt = now;
			ClientPlayNetworking.send(new FarmDetailsPayload.Request(table(), selected));
		}
		return details != null && details.key().equals(selected) ? details : null;
	}

	public static List<FarmLoader.Entry> farms() {
		return farms;
	}

	public static List<LoadedFarmsPayload.InviteEntry> invites() {
		return invites;
	}

	public static FarmListing.Show show() {
		return show;
	}

	public static FarmListing.Sort sort() {
		return sort;
	}

	/** The dimension shown, or empty for every dimension. */
	public static String dimension() {
		return dimension;
	}

	public static void nextShow() {
		show = FarmListing.Show.values()[(show.ordinal() + 1) % FarmListing.Show.values().length];
	}

	public static void nextSort() {
		sort = FarmListing.Sort.values()[(sort.ordinal() + 1) % FarmListing.Sort.values().length];
	}

	/** Every dimension, then each dimension the player has farms in. */
	public static void nextDimension() {
		List<String> dimensions = FarmListing.dimensions(farms);
		int next = dimensions.indexOf(dimension) + 1;
		dimension = next >= dimensions.size() ? "" : dimensions.get(next);
	}

	/** Where the local player stands, for distances. */
	public static FarmListing.Viewer viewer() {
		MinecraftClient client = MinecraftClient.getInstance();
		return client.player == null ? null : new FarmListing.Viewer(client.player.getWorld().getRegistryKey().getValue().toString(), client.player.getBlockPos());
	}

	/** Places in {@link #farms()} to show for this search, with the current filters and sort. */
	public static List<Integer> shown(String query, Function<FarmLoader.Entry, String> name) {
		if (!dimension.isEmpty() && !FarmListing.dimensions(farms).contains(dimension)) {
			dimension = "";
		}
		return FarmListing.shown(farms, name, query, show, dimension, sort, viewer());
	}

	public static int limit() {
		return limit;
	}

	public static boolean received() {
		return received;
	}

	public static long loaded() {
		return farms.stream().filter(farm -> farm.role() == FarmRole.OWNER && farm.loaded()).count();
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
		return FarmListing.indexOfKey(farms, selected);
	}

	public static void invalidate() {
		requested = false;
		received = false;
	}

	public static void toggle(String key) {
		send(key);
	}

	public static void preference(String key, FarmPreferencePayload.Operation operation) {
		if (ClientPlayNetworking.canSend(FarmPreferencePayload.ID)) {
			ClientPlayNetworking.send(new FarmPreferencePayload(table(), key, operation));
		}
	}

	public static void rename(String key, String name) {
		if (ClientPlayNetworking.canSend(FarmRenamePayload.ID)) {
			ClientPlayNetworking.send(new FarmRenamePayload(table(), key, name));
		}
	}

	public static void invite(String key, String playerName, FarmRole role) {
		if (ClientPlayNetworking.canSend(FarmInvitePayload.ID)) {
			ClientPlayNetworking.send(new FarmInvitePayload(table(), key, playerName, role));
		}
	}

	public static void removeCollaborator(String key, UUID collaboratorUuid) {
		if (ClientPlayNetworking.canSend(FarmCollaboratorPayload.ID)) {
			ClientPlayNetworking.send(new FarmCollaboratorPayload(table(), key, collaboratorUuid));
			detailsAskedAt = 0L;
		}
	}

	public static void respond(String key, UUID inviteId, boolean accept) {
		if (ClientPlayNetworking.canSend(FarmInviteResponsePayload.ID)) {
			ClientPlayNetworking.send(new FarmInviteResponsePayload(table(), key, inviteId, accept));
		}
	}

	/** Repairs, takes down, or forgets exactly this farm even if the server's list changed since it was drawn. */
	public static void care(BlockPos table, String key, FarmCarePayload.Action action) {
		if (ClientPlayNetworking.canSend(FarmCarePayload.ID)) {
			ClientPlayNetworking.send(new FarmCarePayload(table, key, action));
		}
	}

	private static void send(String toggle) {
		if (ClientPlayNetworking.canSend(LoadedFarmsPayload.Request.ID)) {
			ClientPlayNetworking.send(new LoadedFarmsPayload.Request(table(), toggle));
		}
	}

	private static BlockPos table() {
		MinecraftClient client = MinecraftClient.getInstance();
		return client.player != null && client.player.currentScreenHandler instanceof DreamTableScreenHandler handler
				? handler.getTablePos() : BlockPos.ORIGIN;
	}
}
