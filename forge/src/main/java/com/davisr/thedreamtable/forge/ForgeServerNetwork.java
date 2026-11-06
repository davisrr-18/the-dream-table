package com.davisr.thedreamtable.forge;

import com.davisr.thedreamtable.TheDreamTableMod;
import com.davisr.thedreamtable.network.NetworkProtocol;
import com.davisr.thedreamtable.network.codec.CustomPayload;
import com.davisr.thedreamtable.network.codec.PacketCodec;
import com.davisr.thedreamtable.network.codec.PayloadTypes;
import com.davisr.thedreamtable.network.codec.RegistryByteBuf;
import com.davisr.thedreamtable.platform.ServerNetwork;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Forge networking for the server side, on one channel that carries every payload in an {@link Envelope}: the payload's
 * id, then its body. The channel is built by {@link #build()} once setup registered the payloads.
 */
final class ForgeServerNetwork implements ServerNetwork {
	private final Map<Identifier, Handler<CustomPayload>> handlers = new ConcurrentHashMap<>();
	private SimpleChannel channel;

	/** One payload on the wire. */
	record Envelope(CustomPayload payload, PacketByteBuf body) {
		/** Encoded up front, so a payload that cannot be written is dropped by {@link #of} instead of failing inside Forge's send. */
		static @Nullable Envelope of(CustomPayload payload) {
			PacketByteBuf body = PayloadTypes.encodeOrWarn(payload);
			return body == null ? null : new Envelope(payload, body);
		}

		void write(PacketByteBuf buf) {
			buf.writeIdentifier(payload.getId().id());
			buf.writeBytes(body, body.readerIndex(), body.readableBytes());
		}

		static Envelope read(PacketByteBuf buf) {
			// An id this install never registered fails here, and Forge drops the connection.
			return new Envelope(PayloadTypes.decode(new CustomPayload.Id<>(buf.readIdentifier()), buf), null);
		}
	}

	@Override
	public <T extends CustomPayload> void registerServerToClient(CustomPayload.Id<T> id, PacketCodec<RegistryByteBuf, T> codec) {
		checkOpen();
		PayloadTypes.register(id, codec);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends CustomPayload> void registerClientToServer(CustomPayload.Id<T> id, PacketCodec<RegistryByteBuf, T> codec, Handler<T> handler) {
		checkOpen();
		PayloadTypes.register(id, codec);
		handlers.put(id.id(), (Handler<CustomPayload>) handler);
	}

	private void checkOpen() {
		if (channel != null) {
			throw new IllegalStateException("Payloads must be registered during mod setup, before the Forge channel is built");
		}
	}

	/**
	 * Builds the channel. Its protocol version is {@link NetworkProtocol#VERSION} (shared with the other loaders), so
	 * Forge itself refuses a client and a server whose payloads differ, and a client without the mod.
	 */
	void build() {
		channel = NetworkRegistry.newSimpleChannel(new Identifier(TheDreamTableMod.MOD_ID, "main"),
				() -> NetworkProtocol.VERSION, NetworkProtocol::compatible, NetworkProtocol::compatible);
		channel.messageBuilder(Envelope.class, 0)
				.encoder(Envelope::write)
				.decoder(Envelope::read)
				.consumerMainThread(this::receive)
				.add();
	}

	private void receive(Envelope envelope, Supplier<NetworkEvent.Context> context) {
		ServerPlayerEntity sender = context.get().getSender();
		if (sender == null) {
			ClientReceivers.receive(envelope.payload());
			return;
		}
		// Only payloads registered as client-to-server are handled; a client sending any other id is ignored.
		Handler<CustomPayload> handler = handlers.get(envelope.payload().getId().id());
		if (handler != null) {
			handler.handle(envelope.payload(), sender);
		}
	}

	@Override
	public void send(ServerPlayerEntity player, CustomPayload payload) {
		// A fake player has no connection to send on.
		Envelope envelope = player.networkHandler == null ? null : Envelope.of(payload);
		if (envelope != null) {
			channel.send(PacketDistributor.PLAYER.with(() -> player), envelope);
		}
	}

	@Override
	public boolean canSend(ServerPlayerEntity player, CustomPayload.Id<?> id) {
		// The channel is all or nothing: a client that has it registered every payload on it.
		return player.networkHandler != null && channel.isRemotePresent(player.networkHandler.connection);
	}

	SimpleChannel channel() {
		return channel;
	}
}
