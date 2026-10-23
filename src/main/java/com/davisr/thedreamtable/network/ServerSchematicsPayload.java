package com.davisr.thedreamtable.network;

import com.davisr.thedreamtable.TheDreamTableMod;
import com.davisr.thedreamtable.farm.FarmOptions.FarmOption;
import com.davisr.thedreamtable.farm.FarmTemplate;
import com.davisr.thedreamtable.farm.FarmTemplates;
import com.davisr.thedreamtable.farm.TemplateCodec;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
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
 * server's files rather than its own. The farms themselves follow on request, one at a time, as {@link Farm}.
 */
public record ServerSchematicsPayload(List<Entry> schematics) implements CustomPayload {
	public static final Id<ServerSchematicsPayload> ID = new Id<>(Identifier.of(TheDreamTableMod.MOD_ID, "server_schematics"));
	private static final int MAX_SCHEMATICS = 1024;

	/** One schematic: its id, icon, display name and block count. */
	public record Entry(String id, String icon, String name, int steps) {
		private static final PacketCodec<RegistryByteBuf, Entry> CODEC = PacketCodec.tuple(
				PacketCodecs.STRING, Entry::id,
				PacketCodecs.STRING, Entry::icon,
				PacketCodecs.STRING, Entry::name,
				PacketCodecs.VAR_INT, Entry::steps,
				Entry::new
		);
	}

	public static final PacketCodec<RegistryByteBuf, ServerSchematicsPayload> CODEC = PacketCodec.tuple(
			Entry.CODEC.collect(PacketCodecs.toList(MAX_SCHEMATICS)), ServerSchematicsPayload::schematics,
			ServerSchematicsPayload::new
	);

	public static void register() {
		PayloadTypeRegistry.playS2C().register(ID, CODEC);
		PayloadTypeRegistry.playS2C().register(Farm.ID, Farm.CODEC);
		PayloadTypeRegistry.playC2S().register(Request.ID, Request.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Request.ID, (payload, context) -> context.server().execute(() -> {
			if (!FarmTemplates.isImported(payload.id())) {
				return;
			}
			FarmTemplate template = FarmTemplates.get(payload.id());
			if (template != null) {
				ServerPlayNetworking.send(context.player(), new Farm(payload.id(), TemplateCodec.encode(template)));
			}
		}));
	}

	public static void send(ServerPlayerEntity player) {
		List<Entry> entries = new ArrayList<>();
		for (FarmOption option : FarmTemplates.imported()) {
			if (entries.size() >= MAX_SCHEMATICS) {
				break;
			}
			String name = option.literalName() == null ? option.id() : option.literalName();
			entries.add(new Entry(option.id(), Registries.ITEM.getId(option.icon()).toString(), name, Math.max(0, FarmTemplates.stepCount(option.id()))));
		}
		ServerPlayNetworking.send(player, new ServerSchematicsPayload(entries));
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

	/** One of the server's schematics, encoded by {@link TemplateCodec}. */
	public record Farm(String id, NbtCompound data) implements CustomPayload {
		public static final Id<Farm> ID = new Id<>(Identifier.of(TheDreamTableMod.MOD_ID, "schematic_farm"));
		public static final PacketCodec<RegistryByteBuf, Farm> CODEC = PacketCodec.tuple(
				PacketCodecs.STRING, Farm::id,
				PacketCodecs.NBT_COMPOUND, Farm::data,
				Farm::new
		);

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}
}
