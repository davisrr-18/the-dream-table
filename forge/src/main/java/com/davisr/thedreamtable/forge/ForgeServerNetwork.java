package com.davisr.thedreamtable.forge;

import com.davisr.thedreamtable.TheDreamTableMod;
import com.davisr.thedreamtable.network.NetworkProtocol;
import com.davisr.thedreamtable.platform.ServerNetwork;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.payload.PayloadProtocol;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Forge networking for the server side, on one channel that carries every payload. Forge builds a channel with all its
 * payloads at once, so the registrations made during setup are kept and applied, in order, by {@link #build()}.
 */
final class ForgeServerNetwork implements ServerNetwork {
	private final List<Consumer<PayloadProtocol<RegistryByteBuf, CustomPayload>>> registrations = new ArrayList<>();
	private Channel<CustomPayload> channel;

	@Override
	public <T extends CustomPayload> void registerServerToClient(CustomPayload.Id<T> id, PacketCodec<RegistryByteBuf, T> codec) {
		register(play -> play.clientbound().addMain(id, codec, (payload, context) -> ClientReceivers.receive(payload)));
	}

	@Override
	public <T extends CustomPayload> void registerClientToServer(CustomPayload.Id<T> id, PacketCodec<RegistryByteBuf, T> codec, Handler<T> handler) {
		register(play -> play.serverbound().addMain(id, codec, (payload, context) -> {
			ServerPlayerEntity player = context.getSender();
			if (player != null) {
				handler.handle(payload, player);
			}
		}));
	}

	private void register(Consumer<PayloadProtocol<RegistryByteBuf, CustomPayload>> registration) {
		if (channel != null) {
			throw new IllegalStateException("Payloads must be registered during mod setup, before the Forge channel is built");
		}
		registrations.add(registration);
	}

	/**
	 * Builds the channel. Its protocol version is {@link NetworkProtocol#VERSION} (shared with the other loaders), so
	 * Forge itself refuses a client and a server whose payloads differ.
	 */
	void build() {
		PayloadProtocol<RegistryByteBuf, CustomPayload> play = ChannelBuilder.named(new Identifier(TheDreamTableMod.MOD_ID, "main"))
				.networkProtocolVersion(Integer.parseInt(NetworkProtocol.VERSION))
				.payloadChannel()
				.play();
		registrations.forEach(registration -> registration.accept(play));
		channel = play.bidirectional().build();
	}

	@Override
	public void send(ServerPlayerEntity player, CustomPayload payload) {
		// A fake player has no connection to send on.
		if (player.networkHandler != null) {
			channel.send(payload, player.networkHandler.getConnection());
		}
	}

	@Override
	public boolean canSend(ServerPlayerEntity player, CustomPayload.Id<?> id) {
		// The channel is all or nothing: a client that has it registered every payload on it.
		return player.networkHandler != null && channel.isRemotePresent(player.networkHandler.getConnection());
	}

	Channel<CustomPayload> channel() {
		return channel;
	}
}
