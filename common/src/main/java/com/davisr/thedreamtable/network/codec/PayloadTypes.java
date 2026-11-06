package com.davisr.thedreamtable.network.codec;

import com.davisr.thedreamtable.TheDreamTableMod;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Every payload's codec by channel, shared by both sides (1.20.5+'s payload type registry). On 1.20.1 the loaders move
 * plain buffers, so they encode and decode through here.
 */
public final class PayloadTypes {
	private static final Map<Identifier, PacketCodec<RegistryByteBuf, ? extends CustomPayload>> CODECS = new ConcurrentHashMap<>();

	private PayloadTypes() {
	}

	public static <T extends CustomPayload> void register(CustomPayload.Id<T> id, PacketCodec<RegistryByteBuf, T> codec) {
		if (CODECS.putIfAbsent(id.id(), codec) != null) {
			throw new IllegalStateException("Payload registered twice: " + id.id());
		}
	}

	public static boolean registered(CustomPayload.Id<?> id) {
		return CODECS.containsKey(id.id());
	}

	/** The payload's body, without its channel. */
	@SuppressWarnings("unchecked")
	public static PacketByteBuf encode(CustomPayload payload) {
		PacketCodec<RegistryByteBuf, CustomPayload> codec = (PacketCodec<RegistryByteBuf, CustomPayload>) CODECS.get(payload.getId().id());
		if (codec == null) {
			throw new IllegalStateException("Payload was never registered: " + payload.getId().id());
		}
		RegistryByteBuf buf = new RegistryByteBuf(Unpooled.buffer());
		codec.encode(buf, payload);
		return buf;
	}

	/**
	 * {@link #encode}, or null with a warning when the payload cannot be written. Loaders encode when sending on 1.20.1,
	 * so a failure would otherwise reach the server tick; on 1.20.5+ it only cost that connection.
	 */
	public static @Nullable PacketByteBuf encodeOrWarn(CustomPayload payload) {
		try {
			return encode(payload);
		} catch (RuntimeException failure) {
			TheDreamTableMod.LOGGER.warn("Dropping payload {}: it could not be encoded", payload.getId().id(), failure);
			return null;
		}
	}

	/** Reads a payload received on {@code id}'s channel; throws when it was never registered or the body is malformed. */
	@SuppressWarnings("unchecked")
	public static <T extends CustomPayload> T decode(CustomPayload.Id<T> id, PacketByteBuf body) {
		PacketCodec<RegistryByteBuf, T> codec = (PacketCodec<RegistryByteBuf, T>) CODECS.get(id.id());
		if (codec == null) {
			throw new IllegalStateException("Payload was never registered: " + id.id());
		}
		return codec.decode(new RegistryByteBuf(body));
	}
}
