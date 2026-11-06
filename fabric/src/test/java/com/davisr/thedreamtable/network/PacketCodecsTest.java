package com.davisr.thedreamtable.network;

import com.davisr.thedreamtable.GameSetup;
import com.davisr.thedreamtable.network.codec.CustomPayload;
import com.davisr.thedreamtable.network.codec.PacketCodec;
import com.davisr.thedreamtable.network.codec.PacketCodecs;
import com.davisr.thedreamtable.network.codec.PayloadTypes;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The bounds the 1.20.1 rebuild of the payload codecs must keep, as 1.20.5+ does. */
class PacketCodecsTest {
	private static final PacketCodec<PacketByteBuf, List<Integer>> TWO = PacketCodecs.VAR_INT.collect(PacketCodecs.toList(2));

	@BeforeAll
	static void bootstrap() {
		GameSetup.bootstrap();
	}

	private static PacketByteBuf buf() {
		return new PacketByteBuf(Unpooled.buffer());
	}

	@Test
	void aBoundedListReadsBackAndRefusesMoreOnBothEnds() {
		PacketByteBuf out = buf();
		TWO.encode(out, List.of(4, 5));
		assertEquals(List.of(4, 5), TWO.decode(out));
		assertThrows(RuntimeException.class, () -> TWO.encode(buf(), List.of(1, 2, 3)));
		PacketByteBuf crafted = buf();
		crafted.writeVarInt(3);
		assertThrows(RuntimeException.class, () -> TWO.decode(crafted));
		PacketByteBuf negative = buf();
		negative.writeVarInt(-1);
		assertThrows(RuntimeException.class, () -> TWO.decode(negative));
	}

	@Test
	void aBoundedStringRefusesALongerOne() {
		PacketCodec<PacketByteBuf, String> four = PacketCodecs.string(4);
		assertThrows(RuntimeException.class, () -> four.encode(buf(), "abcde"));
		PacketByteBuf crafted = buf();
		PacketCodecs.STRING.encode(crafted, "abcde");
		assertThrows(RuntimeException.class, () -> four.decode(crafted));
	}

	@Test
	void aMissingCompoundIsRefused() {
		PacketByteBuf empty = buf();
		empty.writeByte(0);
		assertThrows(RuntimeException.class, () -> PacketCodecs.NBT_COMPOUND.decode(empty));
	}

	@Test
	void anUnknownChannelIsRefusedAndAnUnregisteredPayloadIsNotSent() {
		CustomPayload.Id<SessionSyncPayload> unknown = new CustomPayload.Id<>(new Identifier("the_dream_table", "never_registered"));
		assertThrows(IllegalStateException.class, () -> PayloadTypes.decode(unknown, buf()));
		CustomPayload stray = () -> unknown;
		assertNull(PayloadTypes.encodeOrWarn(stray));
	}
}
