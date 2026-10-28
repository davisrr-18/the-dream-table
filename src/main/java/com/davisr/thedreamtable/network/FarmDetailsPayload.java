package com.davisr.thedreamtable.network;

import com.davisr.thedreamtable.TheDreamTableMod;
import com.davisr.thedreamtable.farm.FarmAreas;
import com.davisr.thedreamtable.farm.FarmAction;
import com.davisr.thedreamtable.farm.FarmDirectory;
import com.davisr.thedreamtable.farm.FarmGrant;
import com.davisr.thedreamtable.farm.FarmLoader;
import com.davisr.thedreamtable.farm.FarmRole;
import com.davisr.thedreamtable.farm.FarmStorage;
import com.davisr.thedreamtable.farm.FarmTemplate;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.UserCache;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * One finished farm's storage, size and condition for its My Farms page: items stored against the room there is, how
 * many chests and barrels, the items held, most first, the farm's block count, and, for a repair, the blocks missing,
 * those the player changed, and the items the missing ones take. Owners additionally receive the bounded collaborator
 * list; other roles never receive collaborator identities.
 * Item counts travel apart from the items, since a total can be far above a stack.
 */
public record FarmDetailsPayload(String key, boolean loaded, int stored, int capacity, int containers, int blocks, List<String> items, List<Integer> counts,
		int missing, int changed, List<String> needs, List<Integer> needCounts, List<CollaboratorEntry> collaborators) implements CustomPayload {
	public static final Id<FarmDetailsPayload> ID = new Id<>(Identifier.of(TheDreamTableMod.MOD_ID, "farm_details"));
	private static final int SHOWN_ITEMS = 8;
	private static final int MAX_COLLABORATORS = 64;

	public record CollaboratorEntry(UUID playerUuid, String playerName, FarmRole role) {
	}
	public static final PacketCodec<RegistryByteBuf, FarmDetailsPayload> CODEC = PacketCodec.of(
			(details, buf) -> {
				buf.writeString(details.key(), 256);
				buf.writeBoolean(details.loaded());
				buf.writeVarInt(details.stored());
				buf.writeVarInt(details.capacity());
				buf.writeVarInt(details.containers());
				buf.writeVarInt(details.blocks());
				writeItems(buf, details.items(), details.counts());
				buf.writeVarInt(details.missing());
				buf.writeVarInt(details.changed());
				writeItems(buf, details.needs(), details.needCounts());
				writeCollaborators(buf, details.collaborators());
			},
			buf -> {
				String key = buf.readString(256);
				boolean loaded = buf.readBoolean();
				int stored = buf.readVarInt();
				int capacity = buf.readVarInt();
				int containers = buf.readVarInt();
				int blocks = buf.readVarInt();
				List<String> items = new ArrayList<>();
				List<Integer> counts = new ArrayList<>();
				readItems(buf, items, counts);
				int missing = buf.readVarInt();
				int changed = buf.readVarInt();
				List<String> needs = new ArrayList<>();
				List<Integer> needCounts = new ArrayList<>();
				readItems(buf, needs, needCounts);
				List<CollaboratorEntry> collaborators = readCollaborators(buf);
				return new FarmDetailsPayload(key, loaded, stored, capacity, containers, blocks, items, counts, missing, changed, needs, needCounts, collaborators);
			});

	private static void writeItems(RegistryByteBuf buf, List<String> items, List<Integer> counts) {
		buf.writeVarInt(items.size());
		for (int index = 0; index < items.size(); index++) {
			buf.writeString(items.get(index), 256);
			buf.writeVarInt(counts.get(index));
		}
	}

	private static void readItems(RegistryByteBuf buf, List<String> items, List<Integer> counts) {
		int size = buf.readVarInt();
		if (size < 0 || size > SHOWN_ITEMS) {
			throw new IllegalArgumentException("Invalid farm detail item count: " + size);
		}
		for (int index = 0; index < size; index++) {
			items.add(buf.readString(256));
			counts.add(buf.readVarInt());
		}
	}

	private static void writeCollaborators(RegistryByteBuf buf, List<CollaboratorEntry> collaborators) {
		buf.writeVarInt(collaborators.size());
		for (CollaboratorEntry collaborator : collaborators) {
			buf.writeUuid(collaborator.playerUuid());
			buf.writeString(collaborator.playerName(), 64);
			buf.writeEnumConstant(collaborator.role());
		}
	}

	private static List<CollaboratorEntry> readCollaborators(RegistryByteBuf buf) {
		int size = buf.readVarInt();
		if (size < 0 || size > MAX_COLLABORATORS) {
			throw new IllegalArgumentException("Invalid farm collaborator count: " + size);
		}
		List<CollaboratorEntry> collaborators = new ArrayList<>(size);
		for (int index = 0; index < size; index++) {
			collaborators.add(new CollaboratorEntry(buf.readUuid(), buf.readString(64), buf.readEnumConstant(FarmRole.class)));
		}
		return List.copyOf(collaborators);
	}

	static List<CollaboratorEntry> collaborators(Map<UUID, FarmGrant> grants, FarmRole viewerRole, Function<UUID, String> name) {
		if (viewerRole != FarmRole.OWNER) {
			return List.of();
		}
		return grants.values().stream()
				.sorted(java.util.Comparator.comparingLong(FarmGrant::acceptedAt).thenComparing(grant -> grant.playerUuid().toString()))
				.limit(MAX_COLLABORATORS)
				.map(grant -> new CollaboratorEntry(grant.playerUuid(), name.apply(grant.playerUuid()), grant.role()))
				.toList();
	}

	public static void register() {
		PayloadTypeRegistry.playS2C().register(ID, CODEC);
		PayloadTypeRegistry.playC2S().register(Request.ID, Request.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Request.ID, (payload, context) -> context.server().execute(() -> {
			FarmNetwork.Checked checked = FarmNetwork.authorize(context.player(), payload.table(), payload.key(), FarmAction.VIEW,
					FarmActionResultPayload.Action.DETAILS);
			if (checked != null) {
				send(context.player(), checked);
			}
		}));
	}

	/** Missing is {@link FarmLoader#UNKNOWN} while part of the farm is unloaded. */
	private static void send(ServerPlayerEntity player, FarmNetwork.Checked checked) {
		FarmDirectory.Located located = checked.farm().located();
		String key = located.key().externalForm();
		FarmLoader.Located farm = new FarmLoader.Located(key, located.world(), located.farm());
			FarmTemplate template = farm.farm().layout();
			FarmStorage.Contents contents = template == null ? null : FarmStorage.contents(farm.world(), template, farm.farm().site());
			List<String> items = new ArrayList<>();
			List<Integer> counts = new ArrayList<>();
			if (contents != null) {
				for (ItemStack stack : contents.items().subList(0, Math.min(SHOWN_ITEMS, contents.items().size()))) {
					items.add(Registries.ITEM.getId(stack.getItem()).toString());
					counts.add(stack.getCount());
				}
			}
			int blocks = template == null ? 0 : template.steps().size();
			int missing = FarmLoader.UNKNOWN;
			int changed = 0;
			List<String> needs = new ArrayList<>();
			List<Integer> needCounts = new ArrayList<>();
			if (template != null) {
				FarmAreas.Presence presence = FarmAreas.presence(farm.world(), template, farm.farm().site(), farm.farm().swaps());
				if (!presence.unloaded()) {
					missing = presence.missing();
					changed = presence.changed();
					presence.needs().entrySet().stream()
							.sorted(Map.Entry.<Item, Integer>comparingByValue().reversed())
							.limit(SHOWN_ITEMS)
							.forEach(need -> {
								needs.add(Registries.ITEM.getId(need.getKey()).toString());
								needCounts.add(need.getValue());
							});
				}
			}
			List<CollaboratorEntry> collaborators = collaborators(located.farm().grants(), checked.farm().role(), uuid -> collaboratorName(player, uuid));
			ServerPlayNetworking.send(player, contents == null
					? new FarmDetailsPayload(key, false, 0, 0, 0, blocks, items, counts, missing, changed, needs, needCounts, collaborators)
					: new FarmDetailsPayload(key, true, contents.stored(), contents.capacity(), contents.containers(), blocks, items, counts, missing, changed,
							needs, needCounts, collaborators));
	}

	private static String collaboratorName(ServerPlayerEntity player, UUID uuid) {
		ServerPlayerEntity online = player.getServer().getPlayerManager().getPlayer(uuid);
		if (online != null) {
			return online.getGameProfile().getName();
		}
		UserCache cache = player.getServer().getUserCache();
		return cache == null ? uuid.toString() : cache.getByUuid(uuid).map(GameProfile::getName).orElse(uuid.toString());
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}

	/** Asks for one farm's details while its My Farms page is open. */
	public record Request(BlockPos table, String key) implements CustomPayload {
		public static final Id<Request> ID = new Id<>(Identifier.of(TheDreamTableMod.MOD_ID, "farm_details_request"));
		public static final PacketCodec<RegistryByteBuf, Request> CODEC = PacketCodec.of(
				(request, buf) -> {
					buf.writeBlockPos(request.table());
					buf.writeString(request.key(), 256);
				},
				buf -> new Request(buf.readBlockPos(), buf.readString(256)));

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}
}
