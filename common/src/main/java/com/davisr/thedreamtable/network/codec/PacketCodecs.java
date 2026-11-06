package com.davisr.thedreamtable.network.codec;

import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The basic codecs, as in 1.20.5+ ({@code BlockPos.PACKET_CODEC}, {@code Direction.PACKET_CODEC} and
 * {@code TextCodecs.REGISTRY_PACKET_CODEC} live here, since 1.20.1's classes have no such fields).
 */
public final class PacketCodecs {
	public static final PacketCodec<PacketByteBuf, Boolean> BOOL = PacketCodec.of((value, buf) -> buf.writeBoolean(value), PacketByteBuf::readBoolean);
	public static final PacketCodec<PacketByteBuf, Integer> VAR_INT = PacketCodec.of((value, buf) -> buf.writeVarInt(value), PacketByteBuf::readVarInt);
	public static final PacketCodec<PacketByteBuf, Long> VAR_LONG = PacketCodec.of((value, buf) -> buf.writeVarLong(value), PacketByteBuf::readVarLong);
	public static final PacketCodec<PacketByteBuf, String> STRING = string(32767);
	/** 1.20.1's {@code readNbt()} keeps vanilla's 2 MiB limit, as {@code NBT_COMPOUND} does on 1.20.5+. */
	public static final PacketCodec<PacketByteBuf, NbtCompound> NBT_COMPOUND = PacketCodec.of((value, buf) -> buf.writeNbt(value), buf -> {
		NbtCompound nbt = buf.readNbt();
		if (nbt == null) {
			throw new DecoderException("Missing NBT compound");
		}
		return nbt;
	});
	public static final PacketCodec<PacketByteBuf, BlockPos> BLOCK_POS = PacketCodec.of((value, buf) -> buf.writeBlockPos(value), PacketByteBuf::readBlockPos);
	public static final PacketCodec<PacketByteBuf, Direction> DIRECTION = PacketCodec.of((value, buf) -> buf.writeEnumConstant(value), buf -> buf.readEnumConstant(Direction.class));
	public static final PacketCodec<PacketByteBuf, Text> TEXT = PacketCodec.of((value, buf) -> buf.writeText(value), PacketByteBuf::readText);

	private PacketCodecs() {
	}

	public static PacketCodec<PacketByteBuf, String> string(int maxLength) {
		return PacketCodec.of((value, buf) -> buf.writeString(value, maxLength), buf -> buf.readString(maxLength));
	}

	/** A list of at most {@code max} elements; a longer one is refused on both ends, as on 1.20.5+. */
	public static <B extends PacketByteBuf, V> Function<PacketCodec<B, V>, PacketCodec<B, List<V>>> toList(int max) {
		return element -> PacketCodec.of((list, buf) -> {
			if (list.size() > max) {
				throw new EncoderException(list.size() + " elements exceed max size of: " + max);
			}
			buf.writeVarInt(list.size());
			for (V value : list) {
				element.encode(buf, value);
			}
		}, buf -> {
			int size = buf.readVarInt();
			if (size < 0 || size > max) {
				throw new DecoderException(size + " elements exceed max size of: " + max);
			}
			List<V> list = new ArrayList<>(Math.min(size, 65536));
			for (int index = 0; index < size; index++) {
				list.add(element.decode(buf));
			}
			return list;
		});
	}
}
