package com.davisr.thedreamtable.network;

import com.davisr.thedreamtable.TheDreamTableMod;
import com.davisr.thedreamtable.farm.FarmOptions.FarmOption;
import com.davisr.thedreamtable.farm.FarmTemplate;
import com.davisr.thedreamtable.farm.FarmTemplates;
import com.davisr.thedreamtable.farm.ImportNote;
import com.davisr.thedreamtable.farm.ImportedStructures;
import com.davisr.thedreamtable.farm.TemplateCodec;
import com.davisr.thedreamtable.platform.Platform;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * The server's My Schematics list, sent when a player opens a table, so a client on another computer shows the
 * server's files rather than its own, with their warnings and the files that can't be used. The farms themselves
 * follow on request, one at a time, as {@link Farm}.
 */
public record ServerSchematicsPayload(List<Entry> schematics, List<ImportedStructures.Rejected> rejected) implements CustomPayload {
	public static final Id<ServerSchematicsPayload> ID = new Id<>(Identifier.of(TheDreamTableMod.MOD_ID, "server_schematics"));
	private static final int MAX_SCHEMATICS = 1024;

	private static final int MAX_NOTES = 8;
	/** Each request encodes a whole farm; a client fetches one per farm it draws, so this is far above a person's use. */
	private static final RequestThrottle REQUESTS = new RequestThrottle(20, RequestThrottle.DEFAULT_WINDOW_TICKS);

	/** One schematic: its id, icon, display name, block count, file version and warnings. */
	public record Entry(String id, String icon, String name, int steps, long version, List<ImportNote> notes) {
		private static final PacketCodec<RegistryByteBuf, Entry> CODEC = PacketCodec.tuple(
				PacketCodecs.STRING, Entry::id,
				PacketCodecs.STRING, Entry::icon,
				PacketCodecs.STRING, Entry::name,
				PacketCodecs.VAR_INT, Entry::steps,
				PacketCodecs.VAR_LONG, Entry::version,
				ImportNote.CODEC.collect(PacketCodecs.toList(MAX_NOTES)), Entry::notes,
				Entry::new
		);
	}

	private static final PacketCodec<RegistryByteBuf, ImportedStructures.Rejected> REJECTED = PacketCodec.tuple(
			PacketCodecs.STRING, ImportedStructures.Rejected::name,
			ImportNote.CODEC, ImportedStructures.Rejected::reason,
			ImportedStructures.Rejected::new
	);

	public static final PacketCodec<RegistryByteBuf, ServerSchematicsPayload> CODEC = PacketCodec.tuple(
			Entry.CODEC.collect(PacketCodecs.toList(MAX_SCHEMATICS)), ServerSchematicsPayload::schematics,
			REJECTED.collect(PacketCodecs.toList(MAX_SCHEMATICS)), ServerSchematicsPayload::rejected,
			ServerSchematicsPayload::new
	);

	public static void register() {
		Platform.network().registerServerToClient(ID, CODEC);
		Platform.network().registerServerToClient(Farm.ID, Farm.CODEC);
		REQUESTS.forgetOnDisconnect();
		Platform.network().registerClientToServer(Request.ID, Request.CODEC, (payload, player) -> {
			if (!REQUESTS.allow(player.getUuid(), player.getServer().getTicks())) {
				return;
			}
			if (!FarmTemplates.isImported(payload.id()) && !FarmTemplates.isSaved(payload.id())) {
				return;
			}
			// The version is read first: a file edited in between only makes the client see an older tag and ask again.
			long version = FarmTemplates.version(payload.id());
			FarmTemplate template = FarmTemplates.get(payload.id());
			if (template != null) {
				Platform.network().send(player, new Farm(payload.id(), version, TemplateCodec.encode(template)));
			}
		});
	}

	public static void send(ServerPlayerEntity player) {
		List<Entry> entries = new ArrayList<>();
		for (FarmOption option : FarmTemplates.imported()) {
			if (entries.size() >= MAX_SCHEMATICS) {
				break;
			}
			String name = option.literalName() == null ? option.id() : option.literalName();
			List<ImportNote> notes = FarmTemplates.notes(option.id());
			entries.add(new Entry(option.id(), Registries.ITEM.getId(option.icon()).toString(), name, Math.max(0, FarmTemplates.stepCount(option.id())), FarmTemplates.version(option.id()),
					notes.subList(0, Math.min(MAX_NOTES, notes.size()))));
		}
		List<ImportedStructures.Rejected> rejected = FarmTemplates.rejected();
		Platform.network().send(player, new ServerSchematicsPayload(entries, rejected.subList(0, Math.min(MAX_SCHEMATICS, rejected.size()))));
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}

	/** A client asking for one of the server's schematics it needs to draw. */
	public record Request(String id) implements CustomPayload {
		public static final Id<Request> ID = new Id<>(Identifier.of(TheDreamTableMod.MOD_ID, "schematic_request"));
		public static final PacketCodec<RegistryByteBuf, Request> CODEC = PacketCodec.tuple(
				PacketCodecs.STRING, Request::id,
				Request::new
		);

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** One of the server's schematics, encoded by {@link TemplateCodec}, with the file version it was read at (0 for a saved design). */
	public record Farm(String id, long version, NbtCompound data) implements CustomPayload {
		public static final Id<Farm> ID = new Id<>(Identifier.of(TheDreamTableMod.MOD_ID, "schematic_farm"));
		public static final PacketCodec<RegistryByteBuf, Farm> CODEC = PacketCodec.tuple(
				PacketCodecs.STRING, Farm::id,
				PacketCodecs.VAR_LONG, Farm::version,
				PacketCodecs.NBT_COMPOUND, Farm::data,
				Farm::new
		);

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}
}
