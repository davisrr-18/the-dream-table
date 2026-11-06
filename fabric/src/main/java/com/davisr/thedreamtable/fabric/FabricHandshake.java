package com.davisr.thedreamtable.fabric;

import com.davisr.thedreamtable.TheDreamTableMod;
import com.davisr.thedreamtable.network.NetworkProtocol;
import com.davisr.thedreamtable.network.codec.CustomPayload;
import com.davisr.thedreamtable.network.codec.PacketCodec;
import com.davisr.thedreamtable.network.codec.PacketCodecs;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerLoginConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerLoginNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Fabric's version handshake, run as a login query before the player joins (1.20.1 has no configuration phase). Forge
 * refuses a client whose channel version differs by itself; Fabric has no such check, so a client and a server with
 * different codecs would connect and then fail on opaque decode errors. The server asks every client for its protocol
 * and disconnects it with a translatable message when it differs.
 *
 * <p>A client without the mod answers that it does not understand the query, so it still joins (the play-phase
 * {@code canSend} guards keep the mod's payloads away from it, as before).
 */
public final class FabricHandshake {
	/** The query's channel; the client's answer comes back on it. */
	static final CustomPayload.Id<Request> REQUEST_ID = new CustomPayload.Id<>(new Identifier(TheDreamTableMod.MOD_ID, "handshake"));
	private static final int MAX_FIELD = 64;

	/** Server to client: the server's protocol and mod version. */
	record Request(String protocol, String version) implements CustomPayload {
		static final PacketCodec<PacketByteBuf, Request> CODEC = PacketCodec.tuple(
				PacketCodecs.string(MAX_FIELD), Request::protocol,
				PacketCodecs.string(MAX_FIELD), Request::version,
				Request::new);

		@Override
		public Id<? extends CustomPayload> getId() {
			return REQUEST_ID;
		}
	}

	/** Client to server: the client's protocol and mod version. */
	record Reply(String protocol, String version) implements CustomPayload {
		static final PacketCodec<PacketByteBuf, Reply> CODEC = PacketCodec.tuple(
				PacketCodecs.string(MAX_FIELD), Reply::protocol,
				PacketCodecs.string(MAX_FIELD), Reply::version,
				Reply::new);

		@Override
		public Id<? extends CustomPayload> getId() {
			return REQUEST_ID;
		}
	}

	private FabricHandshake() {
	}

	/** Registers the server's query and check. Runs from the common entrypoint. */
	static void register() {
		ServerLoginConnectionEvents.QUERY_START.register((handler, server, sender, synchronizer) ->
				sender.sendPacket(REQUEST_ID.id(), encode(Request.CODEC, new Request(NetworkProtocol.VERSION, modVersion()))));
		ServerLoginNetworking.registerGlobalReceiver(REQUEST_ID.id(), (server, handler, understood, buf, synchronizer, sender) -> {
			if (!understood) {
				return;
			}
			Reply reply = Reply.CODEC.decode(buf);
			if (!NetworkProtocol.compatible(reply.protocol())) {
				TheDreamTableMod.LOGGER.warn("Refusing a client with The Dream Table {} (protocol {}); this server runs {} (protocol {})",
						reply.version(), reply.protocol(), modVersion(), NetworkProtocol.VERSION);
				handler.disconnect(mismatch(modVersion(), reply.version()));
			}
		});
	}

	static <T> PacketByteBuf encode(PacketCodec<PacketByteBuf, T> codec, T value) {
		PacketByteBuf buf = PacketByteBufs.create();
		codec.encode(buf, value);
		return buf;
	}

	/** The disconnect message for a client whose protocol differs from the server's. */
	static Text mismatch(String serverVersion, String clientVersion) {
		// The client resolves the key; the fallback keeps a future client without it from showing the raw key.
		return Text.translatableWithFallback("gui.the_dream_table.version_mismatch",
				"The Dream Table version differs from the server's: server %s, you %s", serverVersion, clientVersion);
	}

	static String modVersion() {
		return FabricLoader.getInstance().getModContainer(TheDreamTableMod.MOD_ID)
				.map(container -> container.getMetadata().getVersion().getFriendlyString())
				.orElse("unknown");
	}
}
