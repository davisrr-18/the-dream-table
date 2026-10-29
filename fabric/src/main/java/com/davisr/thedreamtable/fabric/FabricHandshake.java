package com.davisr.thedreamtable.fabric;

import com.davisr.thedreamtable.TheDreamTableMod;
import com.davisr.thedreamtable.network.NetworkProtocol;
import net.fabricmc.fabric.api.networking.v1.FabricServerConfigurationNetworkHandler;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.network.ServerConfigurationNetworkHandler;
import net.minecraft.server.network.ServerPlayerConfigurationTask;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.function.Consumer;

/**
 * Fabric's version handshake, run in the configuration phase before the player joins. NeoForge refuses a client whose
 * payload protocol differs by itself; Fabric has no such check, so a client and a server with different codecs would
 * connect and then fail on opaque decode errors. The server asks a client that has this mod for its protocol and
 * disconnects it with a translatable message when it differs.
 *
 * <p>A client without the mod cannot receive the request, so it is not asked and still joins (the play-phase
 * {@code canSend} guards keep the mod's payloads away from it, as before). A client that has an older version of the
 * mod without the handshake looks the same to the server, so it is let in too.
 */
public final class FabricHandshake {
	static final CustomPayload.Id<Request> REQUEST_ID = new CustomPayload.Id<>(Identifier.of(TheDreamTableMod.MOD_ID, "handshake"));
	static final CustomPayload.Id<Reply> REPLY_ID = new CustomPayload.Id<>(Identifier.of(TheDreamTableMod.MOD_ID, "handshake_reply"));
	private static final int MAX_FIELD = 64;
	private static final ServerPlayerConfigurationTask.Key TASK_KEY = new ServerPlayerConfigurationTask.Key(TheDreamTableMod.MOD_ID + ":handshake");

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
			return REPLY_ID;
		}
	}

	private record Task() implements ServerPlayerConfigurationTask {
		@Override
		public void sendPacket(Consumer<Packet<?>> sender) {
			sender.accept(ServerConfigurationNetworking.createS2CPacket(new Request(NetworkProtocol.VERSION, modVersion())));
		}

		@Override
		public Key getKey() {
			return TASK_KEY;
		}
	}

	private FabricHandshake() {
	}

	/** Registers the handshake payloads on both sides, and the server's check. Runs from the common entrypoint. */
	static void register() {
		PayloadTypeRegistry.configurationS2C().register(REQUEST_ID, Request.CODEC);
		PayloadTypeRegistry.configurationC2S().register(REPLY_ID, Reply.CODEC);
		ServerConfigurationConnectionEvents.BEFORE_CONFIGURE.register(FabricHandshake::ask);
		ServerConfigurationNetworking.registerGlobalReceiver(REPLY_ID, (reply, context) -> {
			ServerConfigurationNetworkHandler handler = context.networkHandler();
			if (NetworkProtocol.compatible(reply.protocol())) {
				((FabricServerConfigurationNetworkHandler) handler).completeTask(TASK_KEY);
			} else {
				TheDreamTableMod.LOGGER.warn("Refusing a client with The Dream Table {} (protocol {}); this server runs {} (protocol {})",
						reply.version(), reply.protocol(), modVersion(), NetworkProtocol.VERSION);
				handler.disconnect(mismatch(modVersion(), reply.version()));
			}
		});
	}

	/** Adds the handshake to a connection whose client has this mod (it registered the request's channel). */
	private static void ask(ServerConfigurationNetworkHandler handler, net.minecraft.server.MinecraftServer server) {
		if (ServerConfigurationNetworking.canSend(handler, REQUEST_ID)) {
			((FabricServerConfigurationNetworkHandler) handler).addTask(new Task());
		}
	}

	/** The disconnect message for a client whose protocol differs from the server's. */
	static Text mismatch(String serverVersion, String clientVersion) {
		return Text.translatable("gui.the_dream_table.version_mismatch", serverVersion, clientVersion);
	}

	static String modVersion() {
		return FabricLoader.getInstance().getModContainer(TheDreamTableMod.MOD_ID)
				.map(container -> container.getMetadata().getVersion().getFriendlyString())
				.orElse("unknown");
	}
}
